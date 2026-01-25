package com.mycom.myapp.sendapp.batch.calculator;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceBatchFailRowDto;
import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscriptionSegmentDto;
import com.mycom.myapp.sendapp.batch.enums.ServiceCategory;
import com.mycom.myapp.sendapp.batch.support.CategoryIdRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository 쿼리 정렬:
 *  - ORDER BY users_id ASC, billing_yyyymm ASC, device_id ASC
 *
 * 위 정렬은 "유저/디바이스 경계"를 잡기엔 충분하지만,
 * 동일 (users_id, device_id) 내부에서 subscription_start_date 오름차순이 보장되지 않으므로
 * Calculator가 device buffer를 모은 뒤 buffer 내부만 정렬하여 세그먼트를 계산합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionSegmentCalculator {
    private final CategoryIdRegistry categoryIdRegistry;

    /**
     * chunk 단위 구독 원천 데이터(rows)를 받아 세그먼트 목록을 생성합니다.
     * rows에는 여러 users_id가 섞여 있을 수 있습니다.
     */
    public List<SubscriptionSegmentDto> calculate (
            Integer targetYyyymm,
            List<SubscribeBillingHistoryRowDto> rows,
            List<MonthlyInvoiceBatchFailRowDto> failRows,
            Long attemptId,
            Map<Long, MonthlyInvoiceRowDto> headerByUserId) {
        List<SubscriptionSegmentDto> segments = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return segments;
        }
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("targetYyyymm is required.");
        }

        // 정산월 기간
        int year = targetYyyymm / 100;
        int month = targetYyyymm % 100;
        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        Long currentUsersId = null;
        Long currentDeviceId = null;

        List<SubscribeBillingHistoryRowDto> buffer = new ArrayList<>();

        for (SubscribeBillingHistoryRowDto row : rows) {
            Long usersId = row.getUsersId();
            Long deviceId = row.getDeviceId();

            // 첫 row
            if (currentUsersId == null) {
                currentUsersId = usersId;
                currentDeviceId = deviceId;
            }

            // (users_id, device_id) 경계가 바뀌면 flush
            if (!currentUsersId.equals(usersId) || !currentDeviceId.equals(deviceId)) {
                flushBuffer(segments, buffer, periodStart, periodEnd, failRows, attemptId, headerByUserId);
                buffer.clear();

                currentUsersId = usersId;
                currentDeviceId = deviceId;
            }

            buffer.add(row);
        }

        // 마지막 flush
        flushBuffer(segments, buffer, periodStart, periodEnd, failRows, attemptId, headerByUserId);

        return segments;
    }

    private void flushBuffer(
            List<SubscriptionSegmentDto> out,
            List<SubscribeBillingHistoryRowDto> buffer,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<MonthlyInvoiceBatchFailRowDto> failRows,
            Long attemptId,
            Map<Long, MonthlyInvoiceRowDto> headerByUserId
    ) {
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // Repository ORDER BY에 subscription_start_date가 없으므로,
        // 동일 (users_id, device_id) 버퍼만 시작일로 정렬해서 정확도를 보장합니다.
        buffer.sort(Comparator.comparing(SubscribeBillingHistoryRowDto::getSubscriptionStartDate));

        List<SubscriptionSegmentDto> temp = new ArrayList<>(); // 확정된 세그먼트 임시 보관 리스트(일괄 추가/롤백 위함)

        // 버퍼는 동일 usersId/deviceId여야 합니다.
        Long usersId = buffer.get(0).getUsersId();
        Long deviceId = buffer.get(0).getDeviceId();

        int monthLength = periodStart.lengthOfMonth(); // 해당 년월의 일수

        int addonCategoryId = categoryIdRegistry.getCategoryId(ServiceCategory.ADDON);
        boolean isPlanSettlementSuccess = true;
        for (int i = 0; i < buffer.size(); i++) {
            SubscribeBillingHistoryRowDto cur = buffer.get(i);

            LocalDate rawStart = cur.getSubscriptionStartDate();
            LocalDate segStart = rawStart.isBefore(periodStart) ? periodStart : rawStart;

            if(cur.getSubscribeCategoryId() == addonCategoryId) {
                // 부가서비스 정산
                // 부가서비스는 이용 기간 세그먼트 계산을 하지 않는다.
                try {
                    out.add(SubscriptionSegmentDto.builder()
                            .usersId(usersId)
                            .deviceId(deviceId)
                            .subscribeBillingHistoryId(cur.getSubscribeBillingHistoryId())
                            .subscribeServiceId(cur.getSubscribeServiceId())
                            .subscribeCategoryId(cur.getSubscribeCategoryId())
                            .serviceName(cur.getServiceName())
                            .segmentStartDate(segStart)
                            .segmentEndDate(periodEnd)
                            .originAmount(cur.getOriginAmount())
                            .discountAmount(cur.getDiscountAmount())
                            .totalAmount(cur.getTotalAmount())
                            .build());
                } catch (Exception e) {
                    MonthlyInvoiceRowDto h = headerByUserId.get(usersId);
                    if (h != null) h.setSettlementSuccess(false);
                    failRows.add(toFailDto(cur, attemptId, headerByUserId, "temporary"));
                }
            } else {
                // 요금제 서비스 정산 로직
                try {
                    if(isPlanSettlementSuccess == false) {
                        // 해당 유저의 특정 기기의 요금제 원천 데이터 정산 도중 한 번이라도 실패한 경우, 해당 기기의 모든 당월 요금제 정산을 실패 처리
                        // 재배치 시 세그먼트 확정 로직 단순성을 위함
                        failRows.add(toFailDto(cur, attemptId, headerByUserId, "temporary"));
                        continue;
                    }
                    LocalDate segEnd = segStart.minusDays(1); // 세그먼트 종료일은 (시작일-1)로 초기화
                    // 이후에 구독한 요금제 데이터 조회
                    boolean foundNextPlan = false; // 이후 요금제 존재 여부
                    int nextSegDataIndx = i + 1;
                    while(nextSegDataIndx < buffer.size()) {
                        SubscribeBillingHistoryRowDto nextRowDto = buffer.get(nextSegDataIndx++);
                        if(nextRowDto.getSubscribeCategoryId() != addonCategoryId) {
                            foundNextPlan = true;
                            LocalDate nextStartMinus1 = nextRowDto.getSubscriptionStartDate().minusDays(1);
                            segEnd = nextStartMinus1.isAfter(periodEnd) ? periodEnd : nextStartMinus1;
                            break;
                        }
                    }
                    if(foundNextPlan == false) {
                        // 이후 요금제를 찾지 못한 경우 segEnd를 월 말일로 설정
                        segEnd = periodEnd;
                    }

                    if (segStart.isAfter(segEnd)) {
                        log.info("세그먼트 시작 일자가 종료 일자보다 이후입니다. 원천 데이터 식별자: {}", buffer.get(i).getSubscribeBillingHistoryId());
                        throw new IllegalArgumentException("원천 데이터 식별자 "+buffer.get(i).getSubscribeBillingHistoryId()+"번의 세그먼트 시작 일자가 종료 일자보다 이후입니다.");
                    }

                    BigDecimal usageRate = BigDecimal.valueOf(segEnd.getDayOfMonth() - segStart.getDayOfMonth() + 1)  // 분자 (long -> BigDecimal)
                            .divide(
                                    BigDecimal.valueOf(monthLength),      // 분모 (int -> BigDecimal)
                                    10,                                   // 소수점 자리수 (Scale, 넉넉하게 설정)
                                    RoundingMode.HALF_DOWN                  // 반내림 모드 (필수)
                            );
                    if(cur.getUsersId() % 100 == 0) {
                        String categoryName = (cur.getSubscribeCategoryId() == 1 ? "요금제" : "기타요금제");
                        log.info("정산 실패. 회원 식별자: {}, 원천 카테고리: {}, 원천 식별자: {}", cur.getUsersId(), categoryName, cur.getSubscribeBillingHistoryId());
                        throw new RuntimeException("정산 배치 실패 예외 처리 테스트용 예외 발생");
                    }
                    // 최종 정산 결과에 대해 명시적 반내림 적용
                    Long originAmount = BigDecimal.valueOf(cur.getOriginAmount())
                            .multiply(usageRate).setScale(0, RoundingMode.DOWN).longValue();
                    Long discountAmount = BigDecimal.valueOf(cur.getDiscountAmount())
                            .multiply(usageRate).setScale(0, RoundingMode.DOWN).longValue();
                    Long totalAmount = originAmount - discountAmount;

                    temp.add(
                            SubscriptionSegmentDto.builder()
                                    .usersId(usersId)
                                    .deviceId(deviceId)
                                    .subscribeBillingHistoryId(cur.getSubscribeBillingHistoryId())
                                    .subscribeServiceId(cur.getSubscribeServiceId())
                                    .subscribeCategoryId(cur.getSubscribeCategoryId())
                                    .serviceName(cur.getServiceName())
                                    .segmentStartDate(segStart)
                                    .segmentEndDate(segEnd)
                                    .originAmount(originAmount)
                                    .discountAmount(discountAmount)
                                    .totalAmount(totalAmount)
                                    .build()
                    );
                } catch (Exception e) {
                    isPlanSettlementSuccess = false; // 해당 유저의 해당 기기의 구독 서비스 정산 성공 여부 false 설정
                    MonthlyInvoiceRowDto h = headerByUserId.get(usersId);
                    if (h != null) h.setSettlementSuccess(false);
                    // 이전에 정산에 성공한 요금제 데이터도 모두 실패로 간주: 모두 실패 이력 리스트에 추가
                    for(SubscriptionSegmentDto dto : temp) {
                        failRows.add(toFailDto(dto, attemptId, headerByUserId, "temporary"));
                    }
                    temp.clear();
                    failRows.add(toFailDto(cur, attemptId, headerByUserId, "temporary"));
                }
            }
        }
        if(isPlanSettlementSuccess) {
            // '요금제' 서비스 정산 성공 시 청구서 상세 영속화 대상에 포함
            out.addAll(temp);
        }
    }

    /**
     * 구독 원천 row 정보를 실패 이력 dto로 변환
     * @param dto 구독 원천 데이터 SubscribeBillingHistoryRowDto
     * @param attemptId 배치 시도 식별자
     * @return
     */
    private MonthlyInvoiceBatchFailRowDto toFailDto(SubscribeBillingHistoryRowDto dto, Long attemptId, Map<Long, MonthlyInvoiceRowDto> headerByUserId, String message) {
        Long invoiceId = headerByUserId.get(dto.getUsersId()).getInvoiceId();
        return MonthlyInvoiceBatchFailRowDto.builder()
                .attemptId(attemptId)
                .errorCode("SUB_SEGMENT_CALC_FAIL")
                .errorMessage(message) // 임시 통일
                .createdAt(LocalDateTime.now())
                .invoiceCategoryId(dto.getSubscribeCategoryId())
                .billingHistoryId(dto.getSubscribeBillingHistoryId())
                .invoiceId(invoiceId)
                .build();
    }

    /**
     * 구독 세그먼트 정보를 실패 이력 dto로 변환
     * @param dto 구독 원천 데이터 SubscribeBillingHistoryRowDto
     * @param attemptId 배치 시도 식별자
     * @return
     */
    private MonthlyInvoiceBatchFailRowDto toFailDto(SubscriptionSegmentDto dto, Long attemptId, Map<Long, MonthlyInvoiceRowDto> headerByUserId, String message) {
        Long invoiceId = headerByUserId.get(dto.getUsersId()).getInvoiceId();
        return MonthlyInvoiceBatchFailRowDto.builder()
                .attemptId(attemptId)
                .errorCode("SUB_SEGMENT_CALC_FAIL")
                .errorMessage(message) // 임시 통일
                .createdAt(LocalDateTime.now())
                .invoiceCategoryId(dto.getSubscribeCategoryId())
                .billingHistoryId(dto.getSubscribeBillingHistoryId())
                .invoiceId(invoiceId)
                .build();
    }
}
