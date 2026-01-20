package com.mycom.myapp.sendapp.batch.calculator;

import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscriptionSegmentDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    /**
     * chunk 단위 구독 원천 데이터(rows)를 받아 세그먼트 목록을 생성합니다.
     * rows에는 여러 users_id가 섞여 있을 수 있습니다.
     */
    public List<SubscriptionSegmentDto> calculate (Integer targetYyyymm, List<SubscribeBillingHistoryRowDto> rows) {
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
                flushBuffer(segments, buffer, periodStart, periodEnd);
                buffer.clear();

                currentUsersId = usersId;
                currentDeviceId = deviceId;
            }

            buffer.add(row);
        }

        // 마지막 flush
        flushBuffer(segments, buffer, periodStart, periodEnd);

        return segments;
    }

    private void flushBuffer(
            List<SubscriptionSegmentDto> out,
            List<SubscribeBillingHistoryRowDto> buffer,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        // Repository ORDER BY에 subscription_start_date가 없으므로,
        // 동일 (users_id, device_id) 버퍼만 시작일로 정렬해서 정확도를 보장합니다.
        buffer.sort(Comparator.comparing(SubscribeBillingHistoryRowDto::getSubscriptionStartDate));

        // 버퍼는 동일 usersId/deviceId여야 합니다.
        Long usersId = buffer.get(0).getUsersId();
        Long deviceId = buffer.get(0).getDeviceId();

        for (int i = 0; i < buffer.size(); i++) {
            SubscribeBillingHistoryRowDto cur = buffer.get(i);

            LocalDate rawStart = cur.getSubscriptionStartDate();
            LocalDate segStart = rawStart.isBefore(periodStart) ? periodStart : rawStart;

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

            out.add(
                    SubscriptionSegmentDto.builder()
                            .usersId(usersId)
                            .deviceId(deviceId)
                            .subscribeBillingHistoryId(cur.getSubscribeBillingHistoryId())
                            .subscribeServiceId(cur.getSubscribeServiceId())
                            .serviceName(cur.getServiceName())
                            .segmentStartDate(segStart)
                            .segmentEndDate(segEnd)
                            .originAmount(cur.getOriginAmount())
                            .discountAmount(cur.getDiscountAmount())
                            .totalAmount(cur.getTotalAmount())
                            .build()
            );
        }
    }
}
