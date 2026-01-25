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
import com.mycom.myapp.sendapp.batch.support.ChunkHeaderBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Step1 Writer:
 * - chunk(=1000) 단위로 "헤더 insert -> invoiceId 조회/주입 -> 원천 조회/정산 -> 상세 insert(즉시) -> 실패 이력 insert -> 정산상태/이력 insert -> 헤더 update" 수행
 *
 * 전제:
 * - Processor는 usersId만 채운 MonthlyInvoiceRowDto를 1건씩 생성해 전달
 * - Reader는 "정산 대상 usersId"만 공급
 * - 원천 조회/상세 insert/flush(메모리 절감)는 Writer에서 수행
 */
@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class MonthlyInvoiceWriter implements ItemWriter<MonthlyInvoiceRowDto> {
    private final MonthlyInvoiceRepository monthlyInvoiceRepository;
    private final MonthlyInvoiceDetailRepository monthlyInvoiceDetailRepository;

    private final SubscribeBillingHistoryRepository subscribeBillingHistoryRepository;
    private final SubscriptionSegmentCalculator subscriptionSegmentCalculator;

    private final MicroPaymentBillingHistoryRepository microPaymentBillingHistoryRepository;

    private final InvoiceSettlementFailureRepository failureRepository;
    private final InvoiceSettlementStatusRepository settlementStatusRepository;
    private final InvoiceSettlementStatusHistoryRepository settlementStatusHistoryRepository;
    private final ChunkHeaderBuffer chunkHeaderBuffer;
    private final MonthlyInvoiceBatchAttemptJdbcRepository monthlyInvoiceBatchAttemptJdbcRepository;

    @Value("#{jobParameters['targetYyyymm']}")
    private Integer targetYyyymm;

    /**
     * Step0에서 JobExecutionContext에 저장한 attemptId 키
     * - 사용자가 "monthlyInvoiceAttemptId"로 저장했다고 했음
     */
    @Value("#{jobExecutionContext['monthlyInvoiceAttemptId']}")
    private Long attemptId;

    private final CategoryIdRegistry categoryIdRegistry;
    private final BatchInvoiceProperties batchInvoiceProperties;

    @Override
    public void write(Chunk<? extends MonthlyInvoiceRowDto> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("jobParameters['targetYyyymm'] is required.");
        }
        if (attemptId == null) {
            throw new IllegalArgumentException("jobExecutionContext['monthlyInvoiceAttemptId'] is required.");
        }

        final LocalDateTime now = LocalDateTime.now();
        final List<MonthlyInvoiceRowDto> inputHeaders = new ArrayList<>(chunk.getItems());

        // 0) Processor에서 받은 헤더 DTO를 usersId 기준 Map으로 구성 (value는 dto 그 자체)
        //    - 이후 DB에서 invoiceId를 일괄 조회한 다음 dto.invoiceId에 주입
        final Map<Long, MonthlyInvoiceRowDto> headerByUserId = new HashMap<>(inputHeaders.size() * 2);
        for (MonthlyInvoiceRowDto h : inputHeaders) {
            if (h == null || h.getUsersId() == null) continue;
            headerByUserId.put(h.getUsersId(), h);
        }

        final List<Long> userIds = headerByUserId.keySet().stream().sorted().collect(Collectors.toList());

        // 1) monthly_invoice 헤더 insert (신규 insert)
        //    - 재실행/중복 상황에서 UK(users_id,billing_yyyymm)로 DuplicateKeyException 가능
        //    - 여기서는 "중복이 나도 이후 invoiceId를 SELECT로 매핑" 가능하게, 중복은 로깅 후 계속 진행
        try {
            monthlyInvoiceRepository.batchInsert(inputHeaders);
        } catch (DuplicateKeyException e) {
            log.warn("monthly_invoice header insert duplicate detected. Continue with id mapping. msg={}", e.getMessage());
        }

        // 2) 방금 insert된(혹은 기존) 헤더의 invoice_id 조회 → 헤더 DTO에 주입
        //    - repository 시그니처: findIdsByUsersIdsAndYyyymm(List<Long> usersIds, Integer billingYyyymm)
        List<MonthlyInvoiceRowDto> idRows = monthlyInvoiceRepository.findIdsByUsersIdsAndYyyymm(userIds, targetYyyymm);
        for (MonthlyInvoiceRowDto row : idRows) {
            MonthlyInvoiceRowDto dto = headerByUserId.get(row.getUsersId());
            if (dto != null) {
                dto.setInvoiceId(row.getInvoiceId());
            }
        }

        // 정산 실패 원천 데이터 정보 저장하는 리스트
        List<MonthlyInvoiceBatchFailRowDto> failRows = new ArrayList<>();

        // 3) 구독 원천 데이터 일괄 조회 → 세그먼트 확정 → 상세 생성 → 즉시 batch insert(여러 번)로 메모리 압박 완화
        //    - 실패 원천은 상세에 반영하지 않고 failRows에만 누적
        writeSubscribeDetails(userIds, headerByUserId, failRows, now);

        // 4) 단건 결제 원천 데이터: 키셋 페이징으로 5000건씩 조회 → 상세 생성 → 즉시 insert 반복
        writeMicroPaymentDetailsKeyset(userIds, headerByUserId, failRows, now);

        // 5) 실패 이력 insert (실패 원천 데이터만 저장; 실패 원천은 "청구서 상세"에 반영하지 않음)
        if (!failRows.isEmpty()) {
            failureRepository.batchInsert(failRows);
        }

        // 6) 정산 상태/이력 insert
        //    - status 테이블: invoice_id PK이므로 "이미 존재하면 insert 실패"를 정책으로(사용자 요구)
        //    - history 테이블: insert-only
        List<SettlementStatusRowDto> statusRows = new ArrayList<>(headerByUserId.size());
        List<SettlementStatusHistoryRowDto> historyRows = new ArrayList<>(headerByUserId.size());

        long settlementSuccessUsersCount = 0; // 이번 청크의 정산 성공 유저 수
        long settlementFailureUsersCount = 0; // 이번 청크의 정산 실패 유저 수
        for (MonthlyInvoiceRowDto h : headerByUserId.values()) {
            SettlementStatus to;
            if(h.getSettlementSuccess() == true) {
                settlementSuccessUsersCount++;
                to = SettlementStatus.COMPLETED;
            } else {
                settlementFailureUsersCount++;
                to = SettlementStatus.FAILED;
            }

            statusRows.add(SettlementStatusRowDto.builder()
                    .invoiceId(h.getInvoiceId())
                    .status(to)
                    .lastAttemptAt(now)
                    .createdAt(now)
                    .build());

            historyRows.add(SettlementStatusHistoryRowDto.builder()
                    .invoiceId(h.getInvoiceId())
                    .attemptId(attemptId)
                    .fromStatus(SettlementStatus.NONE)
                    .toStatus(to)
                    .changedAt(now)
                    .reasonCode(null)
                    .build());
        }

        settlementStatusRepository.batchInsert(statusRows);
        settlementStatusHistoryRepository.batchInsert(historyRows);

        // 7) 헤더 최종 합계/성공여부 반영 update
        //    - 구독/단건 처리에서 header dto의 totals를 누적해둔 값을 반영
        monthlyInvoiceRepository.batchUpdateTotals(new ArrayList<>(headerByUserId.values()));

        // 8) 배치 시도 이력 레코드에 정산 성공/실패 건수 정보 갱신
        ChunkSettlementResultDto dto = new ChunkSettlementResultDto();
        dto.setSuccessCount(settlementSuccessUsersCount);
        dto.setFailCount(settlementFailureUsersCount);
        monthlyInvoiceBatchAttemptJdbcRepository.applyChunkResult(attemptId, dto);

        // 9) 커밋 이후(ChunkListener)에서 Redis 전달할 수 있도록, 성공 헤더 리스트를 인메모리 버퍼에 저장
        var stepCtx = StepSynchronizationManager.getContext();
        if (stepCtx != null) {
            Long stepExecutionId = stepCtx.getStepExecution().getId();
            List<MonthlyInvoiceRowDto> successHeaders = headerByUserId.values().stream()
                    .filter(h -> Boolean.TRUE.equals(h.getSettlementSuccess()))
                    .toList();
            chunkHeaderBuffer.put(stepExecutionId, successHeaders);
        }
    }

    private void writeSubscribeDetails(
            List<Long> userIds,
            Map<Long, MonthlyInvoiceRowDto> headerByUserId,
            List<MonthlyInvoiceBatchFailRowDto> failRows,
            LocalDateTime now
    ) {
        List<SubscribeBillingHistoryRowDto> raw =
                subscribeBillingHistoryRepository.findByUsersIdsAndYyyymm(targetYyyymm, userIds);

        if (raw == null || raw.isEmpty()) {
            return;
        }

        List<SubscriptionSegmentDto> segments = subscriptionSegmentCalculator.calculate(targetYyyymm, raw, failRows, attemptId, headerByUserId);

        // 세그먼트 -> 상세 DTO 변환, SUB_DETAIL_BATCH_SIZE 단위로 끊어서 insert
        List<MonthlyInvoiceDetailRowDto> buffer = new ArrayList<>(Math.min(segments.size(), batchInvoiceProperties.getSubDetailBatchSize()));
        for (SubscriptionSegmentDto s : segments) {
            MonthlyInvoiceRowDto header = headerByUserId.get(s.getUsersId());
            if (header == null || header.getInvoiceId() == null) {
                continue;
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

                // 헤더 합계 누적
                addToHeaderTotals(header, d.getInvoiceCategoryId(), d.getOriginAmount(), d.getDiscountAmount(), d.getTotalAmount());

                if (buffer.size() >= batchInvoiceProperties.getSubDetailBatchSize()) {
                    monthlyInvoiceDetailRepository.batchInsert(buffer);
                    buffer.clear(); // JDBC 기반에서도 "대량 리스트 유지"가 메모리를 잡아먹으므로 즉시 해제
                }
            } catch (Exception ex) {
                header.setSettlementSuccess(false);
                failRows.add(MonthlyInvoiceBatchFailRowDto.builder()
                        .attemptId(attemptId)
                        .errorCode("SUB_DETAIL_BUILD_FAIL")
                        .errorMessage("temporary") // 임시 통일
                        .createdAt(now)
                        .invoiceCategoryId(s.getSubscribeCategoryId())
                        .billingHistoryId(s.getSubscribeBillingHistoryId())
                        .invoiceId(header.getInvoiceId())
                        .build());
            }
        }

        if (!buffer.isEmpty()) {
            monthlyInvoiceDetailRepository.batchInsert(buffer);
            buffer.clear();
        }
    }

    private void writeMicroPaymentDetailsKeyset(
            List<Long> userIds,
            Map<Long, MonthlyInvoiceRowDto> headerByUserId,
            List<MonthlyInvoiceBatchFailRowDto> failRows,
            LocalDateTime now
    ) {
        Long lastSeenId = 0L;
        int microCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.MICRO);

        while (true) {
            // repository는 "키셋 페이징"을 지원한다고 가정
            // - 인덱스: (users_id, billing_yyyymm, micro_payment_billing_history_id)
            List<MicroPaymentBillingHistoryRowDto> page =
                    microPaymentBillingHistoryRepository.findPageByUsersIdsAndYyyymmKeyset(
                            targetYyyymm, userIds, lastSeenId, batchInvoiceProperties.getMicroPageSize()
                    );

            if (page == null || page.isEmpty()) {
                break;
            }

            List<MonthlyInvoiceDetailRowDto> details = new ArrayList<>(page.size());

            for (MicroPaymentBillingHistoryRowDto r : page) {
                MonthlyInvoiceRowDto header = headerByUserId.get(r.getUsersId());
                if (header == null || header.getInvoiceId() == null) {
                    continue;
                }

                try {
                    MonthlyInvoiceDetailRowDto d = MonthlyInvoiceDetailRowDto.builder()
                            .detailId(null)
                            .invoiceId(header.getInvoiceId())
                            .invoiceCategoryId(microCategoryId)
                            .billingHistoryId(r.getMicroPaymentBillingHistoryId())
                            .serviceName(r.getServiceName())
                            .originAmount(nvl(r.getOriginAmount()))
                            .discountAmount(nvl(r.getDiscountAmount()))
                            .totalAmount(nvl(r.getTotalAmount()))
                            .usageStartDate(r.getCreatedAt().toLocalDate())
                            .usageEndDate(r.getCreatedAt().toLocalDate())
                            .createdAt(now)
                            .expiredAt(defaultExpiredAt(now))
                            .build();

                    details.add(d);

                    addToHeaderTotals(header, microCategoryId, d.getOriginAmount(), d.getDiscountAmount(), d.getTotalAmount());
                } catch (Exception ex) {
                    header.setSettlementSuccess(false);
                    failRows.add(MonthlyInvoiceBatchFailRowDto.builder()
                            .attemptId(attemptId)
                            .errorCode("MICRO_DETAIL_BUILD_FAIL")
                            .errorMessage("temporary") // 임시 통일
                            .createdAt(now)
                            .invoiceCategoryId(microCategoryId)
                            .invoiceId(header.getInvoiceId())
                            .billingHistoryId(r.getMicroPaymentBillingHistoryId())
                            .build());
                }

                // 키셋 진행
                lastSeenId = Math.max(lastSeenId, r.getMicroPaymentBillingHistoryId());
            }

            // "조회한 5000건"에 대해 즉시 insert (대량 누적 방지)
            if (!details.isEmpty()) {
                monthlyInvoiceDetailRepository.batchInsert(details);
                details.clear();
            }
        }
    }


    private void addToHeaderTotals(MonthlyInvoiceRowDto header, int categoryId, Long origin, Long discount, Long total) {
        // totalDiscountAmount / totalAmount는 카테고리 상관 없이 누적
        header.setTotalDiscountAmount(nvl(header.getTotalDiscountAmount()) + nvl(discount));
        header.setTotalAmount(nvl(header.getTotalAmount()) + nvl(total));
        int planCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.PLAN);
        int addonCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.ADDON);
        // 카테고리별 합계는 정책에 맞춰 누적
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
