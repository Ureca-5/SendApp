package com.mycom.myapp.sendapp.batch.calculator;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceBatchFailRowDto;
import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscriptionSegmentDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Repository 쿼리 정렬:
 *  - ORDER BY users_id ASC, billing_yyyymm ASC, device_id ASC
 *
 * 위 정렬은 "유저/디바이스 경계"를 잡기엔 충분하지만,
 * 동일 (users_id, device_id) 내부에서 subscription_start_date 오름차순이 보장되지 않으므로
 * Calculator가 device buffer를 모은 뒤 buffer 내부만 정렬하여 세그먼트를 계산합니다.
 */
@Component
public class SubscriptionSegmentCalculator {
    private static final int ADDON_CATEGORY_ID = 2;

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

        boolean isSuccess = true;
        for (int i = 0; i < buffer.size(); i++) {
            SubscribeBillingHistoryRowDto cur = buffer.get(i);

            LocalDate rawStart = cur.getSubscriptionStartDate();
            LocalDate segStart = rawStart.isBefore(periodStart) ? periodStart : rawStart;

            try {
                if(cur.getSubscribeCategoryId() == ADDON_CATEGORY_ID) {
                    // 부가서비스는 이용 기간 세그먼트 계산을 하지 않는다.
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
                    continue;
                }

                LocalDate segEnd;
                if (i + 1 < buffer.size()) {
                    LocalDate nextStartMinus1 = buffer.get(i + 1).getSubscriptionStartDate().minusDays(1);
                    segEnd = nextStartMinus1.isAfter(periodEnd) ? periodEnd : nextStartMinus1;
                } else {
                    segEnd = periodEnd;
                }

                if (segStart.isAfter(segEnd)) {
                    continue;
                }

                long daysOfUse = ChronoUnit.DAYS.between(segStart, segEnd.plusDays(1));
                BigDecimal usageRate = BigDecimal.valueOf(daysOfUse)  // 분자 (long -> BigDecimal)
                        .divide(
                                BigDecimal.valueOf(monthLength),      // 분모 (int -> BigDecimal)
                                10,                                   // 소수점 자리수 (Scale, 넉넉하게 설정)
                                RoundingMode.HALF_DOWN                  // 반내림 모드 (필수)
                        );

                Long originAmount = BigDecimal.valueOf(cur.getOriginAmount())
                        .multiply(usageRate).longValue();
                Long discountAmount = BigDecimal.valueOf(cur.getDiscountAmount())
                        .multiply(usageRate).longValue();
                Long totalAmount = BigDecimal.valueOf(cur.getTotalAmount())
                        .multiply(usageRate).longValue();

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
                isSuccess = false;
                e.printStackTrace();
                if(cur.getSubscribeCategoryId() == ADDON_CATEGORY_ID) {
                    MonthlyInvoiceRowDto h = headerByUserId.get(usersId);
                    if (h != null) h.setSettlementSuccess(false);
                    // 정산 실패 대상이 '부가서비스'인 경우 바로바로 실패 이력에 추가
                    failRows.add(MonthlyInvoiceBatchFailRowDto.builder()
                            .attemptId(attemptId)
                            .errorCode("SUB_SEGMENT_CALC_FAIL")
                            .errorMessage("temporary") // 임시 통일
                            .createdAt(LocalDateTime.now())
                            .invoiceCategoryId(cur.getSubscribeCategoryId())
                            .billingHistoryId(cur.getSubscribeBillingHistoryId())
                            .build());
                }
            }
        }
        if(isSuccess) {
            out.addAll(temp);
        } else {
            // 요금제, 기타 요금제 원천 데이터 하나라도 정산에 실패하는 경우 해당 디바이스의 당월 모든 요금제, 기타 요금제를 실패 처리
            MonthlyInvoiceRowDto h = headerByUserId.get(usersId);
            if (h != null) h.setSettlementSuccess(false);
            for(SubscriptionSegmentDto dto : temp) {
                failRows.add(MonthlyInvoiceBatchFailRowDto.builder()
                        .attemptId(attemptId)
                        .errorCode("SUB_SEGMENT_CALC_FAIL")
                        .errorMessage("temporary") // 임시 통일
                        .createdAt(LocalDateTime.now())
                        .invoiceCategoryId(dto.getSubscribeCategoryId())
                        .billingHistoryId(dto.getSubscribeBillingHistoryId())
                        .build());
            }
        }
    }
}
