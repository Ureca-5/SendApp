package com.mycom.myapp.sendapp.batch.writer;

import com.mycom.myapp.sendapp.batch.calculator.SubscriptionSegmentCalculator;
import com.mycom.myapp.sendapp.batch.dto.*;
import com.mycom.myapp.sendapp.batch.enums.ServiceCategory;
import com.mycom.myapp.sendapp.batch.enums.SettlementStatus;
import com.mycom.myapp.sendapp.batch.repository.attempt.ChunkSettlementResultDto;
import com.mycom.myapp.sendapp.batch.repository.attempt.MonthlyInvoiceBatchAttemptJdbcRepository;
import com.mycom.myapp.sendapp.batch.repository.invoice.MonthlyInvoiceDetailRepository;
import com.mycom.myapp.sendapp.batch.repository.invoice.MonthlyInvoiceRepository;
import com.mycom.myapp.sendapp.batch.repository.micropayment.MicroPaymentBillingHistoryRepository;
import com.mycom.myapp.sendapp.batch.repository.settlement.InvoiceSettlementFailureRepository;
import com.mycom.myapp.sendapp.batch.repository.settlement.InvoiceSettlementStatusHistoryRepository;
import com.mycom.myapp.sendapp.batch.repository.settlement.InvoiceSettlementStatusRepository;
import com.mycom.myapp.sendapp.batch.repository.subscribe.SubscribeBillingHistoryRepository;
import com.mycom.myapp.sendapp.batch.support.BatchInvoiceProperties;
import com.mycom.myapp.sendapp.batch.support.CategoryIdRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재시도 배치 Writer
 * - settlement_status가 FAILED인 invoice_id들을 입력으로 받아, batch_fail 원천 데이터 기준으로 상세를 재생성
 * - monthly_invoice totals는 “기존 값 + 재시도 생성 상세 값”으로 누적
 * - 정산 상태가 성공하면 COMPLETED로 갱신, 실패 시 FAILED → FAILED history만 기록
 */
@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceRetryWriter implements ItemWriter<Long> {
    private final MonthlyInvoiceRepository monthlyInvoiceRepository;
    private final MonthlyInvoiceDetailRepository monthlyInvoiceDetailRepository;

    private final SubscribeBillingHistoryRepository subscribeBillingHistoryRepository;
    private final SubscriptionSegmentCalculator subscriptionSegmentCalculator;

    private final MicroPaymentBillingHistoryRepository microPaymentBillingHistoryRepository;

    private final InvoiceSettlementFailureRepository failureRepository;
    private final InvoiceSettlementStatusRepository settlementStatusRepository;
    private final InvoiceSettlementStatusHistoryRepository settlementStatusHistoryRepository;
    private final MonthlyInvoiceBatchAttemptJdbcRepository attemptRepository;

    private final CategoryIdRegistry categoryIdRegistry;
    private final BatchInvoiceProperties batchInvoiceProperties;

    @Value("#{jobExecutionContext['monthlyInvoiceAttemptId']}")
    private Long attemptId;

    @Override
    public void write(Chunk<? extends Long> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (attemptId == null) {
            throw new IllegalArgumentException("jobExecutionContext['monthlyInvoiceAttemptId'] is required.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> invoiceIds = chunk.getItems().stream()
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, MonthlyInvoiceRowDto> headerByInvoiceId = loadHeaders(invoiceIds);

        // 존재하지 않는 invoice_id는 실패 처리
        for (Long invoiceId : invoiceIds) {
            headerByInvoiceId.computeIfAbsent(invoiceId, id ->
                    MonthlyInvoiceRowDto.builder()
                            .invoiceId(id)
                            .settlementSuccess(false)
                            .build()
            );
        }

        int planCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.PLAN);
        int etcPlanCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.ETC_PLAN);
        int microCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.MICRO);

        List<MonthlyInvoiceBatchFailRowDto> newFailRows = new ArrayList<>();
        Set<Long> invoicesWithRetrySource = new HashSet<>();

        // 3) 구독/요금제 실패 원천 재정산
        List<MonthlyInvoiceBatchFailRowDto> subFails =
                failureRepository.findByInvoiceIdsAndCategoryIds(invoiceIds, List.of(planCategoryId, etcPlanCategoryId));
        if (!subFails.isEmpty()) {
            invoicesWithRetrySource.addAll(subFails.stream()
                    .map(MonthlyInvoiceBatchFailRowDto::getInvoiceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            processSubscriptionFailures(headerByInvoiceId, subFails, newFailRows, now);
        }

        // 5) 단건 결제 실패 원천 재정산
        invoicesWithRetrySource.addAll(processMicroFailures(headerByInvoiceId, invoiceIds, microCategoryId, newFailRows, now));

        // 대상 invoice인데 실패 원천 자체가 없으면 실패 상태 유지
        for (Long invoiceId : invoiceIds) {
            if (!invoicesWithRetrySource.contains(invoiceId)) {
                MonthlyInvoiceRowDto header = headerByInvoiceId.get(invoiceId);
                if (header != null) {
                    header.setSettlementSuccess(false);
                }
            }
        }

        // 실패 원천 기록 추가
        if (!newFailRows.isEmpty()) {
            failureRepository.batchInsert(newFailRows);
        }

        // 청구서 합계 갱신
        List<MonthlyInvoiceRowDto> headersToUpdate = headerByInvoiceId.values().stream()
                .filter(h -> h.getUsersId() != null) // 존재하는 헤더만 갱신
                .toList();
        if (!headersToUpdate.isEmpty()) {
            monthlyInvoiceRepository.batchUpdateTotals(headersToUpdate);
        }

        // 정산 상태/이력 갱신
        applyStatusAndHistory(invoiceIds, headerByInvoiceId, now);
    }

    private Map<Long, MonthlyInvoiceRowDto> loadHeaders(List<Long> invoiceIds) {
        Map<Long, MonthlyInvoiceRowDto> map = new HashMap<>();
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return map;
        }
        List<MonthlyInvoiceRowDto> rows = monthlyInvoiceRepository.findByInvoiceIds(invoiceIds);
        for (MonthlyInvoiceRowDto row : rows) {
            if (row.getInvoiceId() == null) continue;
            row.setSettlementSuccess(true); // 기본 성공 가정
            map.put(row.getInvoiceId(), row);
        }
        return map;
    }

    private void processSubscriptionFailures(
            Map<Long, MonthlyInvoiceRowDto> headerByInvoiceId,
            List<MonthlyInvoiceBatchFailRowDto> failRows,
            List<MonthlyInvoiceBatchFailRowDto> newFailRows,
            LocalDateTime now
    ) {
        if (failRows == null || failRows.isEmpty()) {
            return;
        }

        Map<Long, Set<Long>> failIdsByInvoice = new HashMap<>();
        for (MonthlyInvoiceBatchFailRowDto row : failRows) {
            if (row.getInvoiceId() == null || row.getBillingHistoryId() == null) continue;
            failIdsByInvoice.computeIfAbsent(row.getInvoiceId(), k -> new HashSet<>())
                    .add(row.getBillingHistoryId());
        }

        Map<Integer, List<MonthlyInvoiceRowDto>> headersByYyyymm = headerByInvoiceId.values().stream()
                .filter(h -> h.getBillingYyyymm() != null)
                .filter(h -> failIdsByInvoice.containsKey(h.getInvoiceId()))
                .collect(Collectors.groupingBy(MonthlyInvoiceRowDto::getBillingYyyymm));

        for (Map.Entry<Integer, List<MonthlyInvoiceRowDto>> entry : headersByYyyymm.entrySet()) {
            Integer yyyymm = entry.getKey();
            List<MonthlyInvoiceRowDto> headers = entry.getValue();

            List<Long> userIds = headers.stream()
                    .map(MonthlyInvoiceRowDto::getUsersId)
                    .filter(Objects::nonNull)
                    .toList();

            if (userIds.isEmpty()) {
                continue;
            }

            Map<Long, MonthlyInvoiceRowDto> headerByUserId = headers.stream()
                    .filter(h -> h.getUsersId() != null)
                    .collect(Collectors.toMap(MonthlyInvoiceRowDto::getUsersId, Function.identity(), (a, b) -> a));

            List<SubscribeBillingHistoryRowDto> raw =
                    subscribeBillingHistoryRepository.findByUsersIdsAndYyyymm(yyyymm, userIds);

            List<SubscriptionSegmentDto> segments =
                    subscriptionSegmentCalculator.calculate(yyyymm, raw, newFailRows, attemptId, headerByUserId);

            if (segments == null || segments.isEmpty()) {
                continue;
            }

            List<MonthlyInvoiceDetailRowDto> buffer = new ArrayList<>(Math.min(segments.size(), batchInvoiceProperties.getSubDetailBatchSize()));

            for (SubscriptionSegmentDto s : segments) {
                MonthlyInvoiceRowDto header = headerByUserId.get(s.getUsersId());
                if (header == null || header.getInvoiceId() == null) {
                    continue;
                }

                Set<Long> targetFailIds = failIdsByInvoice.get(header.getInvoiceId());
                if (targetFailIds == null || !targetFailIds.contains(s.getSubscribeBillingHistoryId())) {
                    continue; // 이번 재시도 대상이 아닌 세그먼트
                }

                try {
                    MonthlyInvoiceDetailRowDto d = MonthlyInvoiceDetailRowDto.builder()
                            .detailId(null)
                            .invoiceId(header.getInvoiceId())
                            .invoiceCategoryId(s.getSubscribeCategoryId())
                            .billingHistoryId(s.getSubscribeBillingHistoryId())
                            .serviceName(s.getServiceName())
                            .originAmount(nvl(s.getOriginAmount()))
                            .discountAmount(nvl(s.getDiscountAmount()))
                            .totalAmount(nvl(s.getTotalAmount()))
                            .usageStartDate(s.getSegmentStartDate())
                            .usageEndDate(s.getSegmentEndDate())
                            .createdAt(now)
                            .expiredAt(defaultExpiredAt(now))
                            .build();

                    buffer.add(d);
                    if (buffer.size() >= batchInvoiceProperties.getSubDetailBatchSize()) {
                        flushDetails(buffer, headerByInvoiceId, newFailRows, now);
                    }
                } catch (Exception ex) {
                    header.setSettlementSuccess(false);
                    newFailRows.add(buildFailRow("SUB_DETAIL_BUILD_FAIL", ex.getMessage(), now,
                            s.getSubscribeCategoryId(), s.getSubscribeBillingHistoryId(), header.getInvoiceId()));
                }
            }

            flushDetails(buffer, headerByInvoiceId, newFailRows, now);
        }
    }

    private Set<Long> processMicroFailures(
            Map<Long, MonthlyInvoiceRowDto> headerByInvoiceId,
            List<Long> invoiceIds,
            int microCategoryId,
            List<MonthlyInvoiceBatchFailRowDto> newFailRows,
            LocalDateTime now
    ) {
        Set<Long> invoicesWithMicro = new HashSet<>();
        Long lastFailId = 0L;

        while (true) {
            List<MonthlyInvoiceBatchFailRowDto> microFails =
                    failureRepository.findMicroByInvoiceIds(invoiceIds, microCategoryId, lastFailId, batchInvoiceProperties.getMicroPageSize());
            if (microFails == null || microFails.isEmpty()) {
                break;
            }

            invoicesWithMicro.addAll(microFails.stream()
                    .map(MonthlyInvoiceBatchFailRowDto::getInvoiceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));

            List<Long> billingIds = microFails.stream()
                    .map(MonthlyInvoiceBatchFailRowDto::getBillingHistoryId)
                    .filter(Objects::nonNull)
                    .toList();

            Map<Long, MicroPaymentBillingHistoryRowDto> rawById = microPaymentBillingHistoryRepository.findByIds(billingIds)
                    .stream()
                    .collect(Collectors.toMap(MicroPaymentBillingHistoryRowDto::getMicroPaymentBillingHistoryId, Function.identity()));

            List<MonthlyInvoiceDetailRowDto> details = new ArrayList<>(microFails.size());

            for (MonthlyInvoiceBatchFailRowDto fail : microFails) {
                MonthlyInvoiceRowDto header = headerByInvoiceId.get(fail.getInvoiceId());
                if (header == null || header.getInvoiceId() == null) {
                    newFailRows.add(buildFailRow("INVOICE_NOT_FOUND", "missing header for invoice " + fail.getInvoiceId(),
                            now, microCategoryId, fail.getBillingHistoryId(), fail.getInvoiceId()));
                    continue;
                }

                MicroPaymentBillingHistoryRowDto raw = rawById.get(fail.getBillingHistoryId());
                if (raw == null) {
                    header.setSettlementSuccess(false);
                    newFailRows.add(buildFailRow("MICRO_SOURCE_NOT_FOUND", "missing micro payment data",
                            now, microCategoryId, fail.getBillingHistoryId(), fail.getInvoiceId()));
                    continue;
                }

                try {
                    MonthlyInvoiceDetailRowDto d = MonthlyInvoiceDetailRowDto.builder()
                            .detailId(null)
                            .invoiceId(header.getInvoiceId())
                            .invoiceCategoryId(microCategoryId)
                            .billingHistoryId(raw.getMicroPaymentBillingHistoryId())
                            .serviceName(raw.getServiceName())
                            .originAmount(nvl(raw.getOriginAmount()))
                            .discountAmount(nvl(raw.getDiscountAmount()))
                            .totalAmount(nvl(raw.getTotalAmount()))
                            .usageStartDate(raw.getCreatedAt().toLocalDate())
                            .usageEndDate(raw.getCreatedAt().toLocalDate())
                            .createdAt(now)
                            .expiredAt(defaultExpiredAt(now))
                            .build();
                    details.add(d);
                } catch (Exception ex) {
                    header.setSettlementSuccess(false);
                    newFailRows.add(buildFailRow("MICRO_DETAIL_BUILD_FAIL", ex.getMessage(),
                            now, microCategoryId, fail.getBillingHistoryId(), fail.getInvoiceId()));
                }
            }

            flushDetails(details, headerByInvoiceId, newFailRows, now);

            lastFailId = microFails.get(microFails.size() - 1).getFailId();
        }

        return invoicesWithMicro;
    }

    private void flushDetails(
            List<MonthlyInvoiceDetailRowDto> details,
            Map<Long, MonthlyInvoiceRowDto> headerByInvoiceId,
            List<MonthlyInvoiceBatchFailRowDto> newFailRows,
            LocalDateTime now
    ) {
        if (details == null || details.isEmpty()) {
            return;
        }

        try {
            int[] results = monthlyInvoiceDetailRepository.batchInsertIgnore(details);
            for (int i = 0; i < results.length; i++) {
                if (results[i] <= 0) {
                    continue; // 이미 존재(IGNORE) → totals 증가시켜선 안 됨
                }
                MonthlyInvoiceDetailRowDto d = details.get(i);
                MonthlyInvoiceRowDto header = headerByInvoiceId.get(d.getInvoiceId());
                if (header != null) {
                    addToHeaderTotals(header, d.getInvoiceCategoryId(), d.getOriginAmount(), d.getDiscountAmount(), d.getTotalAmount());
                }
            }
        } catch (DataAccessException ex) {
            log.error("monthly_invoice_detail batch insert 실패", ex);
            for (MonthlyInvoiceDetailRowDto d : details) {
                MonthlyInvoiceRowDto header = headerByInvoiceId.get(d.getInvoiceId());
                if (header != null) {
                    header.setSettlementSuccess(false);
                }
                newFailRows.add(buildFailRow("DETAIL_INSERT_FAIL", ex.getMessage(), now,
                        d.getInvoiceCategoryId(), d.getBillingHistoryId(), d.getInvoiceId()));
            }
        } finally {
            details.clear();
        }
    }

    private void applyStatusAndHistory(
            List<Long> invoiceIds,
            Map<Long, MonthlyInvoiceRowDto> headerByInvoiceId,
            LocalDateTime now
    ) {
        List<Long> successInvoiceIds = new ArrayList<>();
        List<SettlementStatusHistoryRowDto> histories = new ArrayList<>(invoiceIds.size());

        for (Long invoiceId : invoiceIds) {
            MonthlyInvoiceRowDto header = headerByInvoiceId.get(invoiceId);
            boolean success = header != null && Boolean.TRUE.equals(header.getSettlementSuccess());
            if (success) {
                successInvoiceIds.add(invoiceId);
            }

            histories.add(SettlementStatusHistoryRowDto.builder()
                    .invoiceId(invoiceId)
                    .attemptId(attemptId)
                    .fromStatus(SettlementStatus.FAILED)
                    .toStatus(success ? SettlementStatus.COMPLETED : SettlementStatus.FAILED)
                    .changedAt(now)
                    .reasonCode(success ? null : "retry failed")
                    .build());
        }

        settlementStatusHistoryRepository.batchInsert(histories);

        if (!successInvoiceIds.isEmpty()) {
            settlementStatusRepository.batchUpdateStatus(successInvoiceIds, SettlementStatus.COMPLETED, now);
        }

        long successCount = successInvoiceIds.size();
        long failCount = invoiceIds.size() - successCount;

        ChunkSettlementResultDto dto = new ChunkSettlementResultDto();
        dto.setSuccessCount(successCount);
        dto.setFailCount(failCount);
        attemptRepository.applyChunkResult(attemptId, dto);
    }

    private MonthlyInvoiceBatchFailRowDto buildFailRow(
            String errorCode,
            String errorMessage,
            LocalDateTime now,
            Integer invoiceCategoryId,
            Long billingHistoryId,
            Long invoiceId
    ) {
        return MonthlyInvoiceBatchFailRowDto.builder()
                .attemptId(attemptId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .createdAt(now)
                .invoiceCategoryId(invoiceCategoryId)
                .billingHistoryId(billingHistoryId)
                .invoiceId(invoiceId)
                .build();
    }

    private void addToHeaderTotals(MonthlyInvoiceRowDto header, int categoryId, Long origin, Long discount, Long total) {
        header.setTotalDiscountAmount(nvl(header.getTotalDiscountAmount()) + nvl(discount));
        header.setTotalAmount(nvl(header.getTotalAmount()) + nvl(total));

        int planCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.PLAN);
        int addonCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.ADDON);

        if (categoryId == planCategoryId) {
            header.setTotalPlanAmount(nvl(header.getTotalPlanAmount()) + nvl(origin));
        } else if (categoryId == addonCategoryId) {
            header.setTotalAddonAmount(nvl(header.getTotalAddonAmount()) + nvl(origin));
        } else {
            header.setTotalEtcAmount(nvl(header.getTotalEtcAmount()) + nvl(origin));
        }
    }

    private Long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private LocalDate defaultExpiredAt(LocalDateTime now) {
        return now.toLocalDate().plusYears(5);
    }
}
