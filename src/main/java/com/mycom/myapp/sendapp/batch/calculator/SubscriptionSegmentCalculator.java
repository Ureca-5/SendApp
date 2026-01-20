package com.mycom.myapp.sendapp.batch.calculator;

import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscriptionSegmentDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class SubscriptionSegmentCalculator {
    /**
     * 구독 이력 원천 데이터를 기반으로 정산 월의 유효 세그먼트를 계산합니다.
     *
     * <p>처리 절차:
     * 1. 정산 월의 시작/종료일 확정 <br>
     * 2. 원천 데이터 순회 (동일 기기 단위로 버퍼링) <br>
     * 3. 기기 변경 시점에 버퍼 내 데이터를 날짜순 정렬 <br>
     * 4. 정렬된 이력을 순회하며 세그먼트 시작/종료일(연속성 고려) 산출
     * </p>
     */
    public List<SubscriptionSegmentDto> calculate(
            Long usersId,
            List<SubscribeBillingHistoryRowDto> rawHistoryRows,
            Integer targetYyyymm
    ) {
        if (rawHistoryRows == null || rawHistoryRows.isEmpty()) {
            return List.of();
        }

        // 1. 정산월의 시작일/종료일 계산
        int year  = targetYyyymm / 100;
        int month = targetYyyymm % 100;
        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd   = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        List<SubscriptionSegmentDto> resultSegments = new ArrayList<>();

        // 한 기기(Device)의 이력을 모아둘 임시 버퍼
        List<SubscribeBillingHistoryRowDto> deviceBuffer = new ArrayList<>();

        for (int i = 0; i < rawHistoryRows.size(); i++) {
            SubscribeBillingHistoryRowDto currentRow = rawHistoryRows.get(i);

            // 버퍼에 현재 행 추가
            deviceBuffer.add(currentRow);

            // 다음 행 확인 (마지막 행이거나, 다음 행의 DeviceID가 달라지면 처리 시작)
            boolean isLastRow = (i == rawHistoryRows.size() - 1);
            boolean isDeviceChanged = false;
            if (!isLastRow) {
                Long nextDeviceId = rawHistoryRows.get(i + 1).getDeviceId();
                if (!nextDeviceId.equals(currentRow.getDeviceId())) {
                    isDeviceChanged = true;
                }
            }

            if (isLastRow || isDeviceChanged) {
                // [핵심] 한 기기의 데이터를 날짜순으로 정렬
                deviceBuffer.sort(Comparator.comparing(SubscribeBillingHistoryRowDto::getSubscriptionStartDate));

                // 정렬된 데이터를 기반으로 세그먼트 계산
                processDeviceBuffer(usersId, deviceBuffer, periodStart, periodEnd, resultSegments);

                // 처리가 끝났으므로 버퍼 초기화
                deviceBuffer.clear();
            }
        }

        return resultSegments;
    }

    /**
     * 한 기기(Device)의 정렬된 이력 리스트를 받아 세그먼트를 생성하는 메서드
     */
    private void processDeviceBuffer(
            Long usersId,
            List<SubscribeBillingHistoryRowDto> sortedRows,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<SubscriptionSegmentDto> resultSegments
    ) {
        for (int j = 0; j < sortedRows.size(); j++) {
            SubscribeBillingHistoryRowDto current = sortedRows.get(j);

            LocalDate start = current.getSubscriptionStartDate();
            // 세그먼트 시작일 = max(월초, 구독시작일)
            LocalDate segmentStart = start.isBefore(periodStart) ? periodStart : start;

            LocalDate segmentEnd;

            // 내 다음 이력이 있는지 확인
            if (j + 1 < sortedRows.size()) {
                // 다음 이력의 시작일 하루 전이 나의 종료일
                LocalDate nextStart = sortedRows.get(j + 1).getSubscriptionStartDate();
                LocalDate potentialEnd = nextStart.minusDays(1);

                // 단, 그 날짜가 월말을 넘어가면 월말까지만
                segmentEnd = potentialEnd.isAfter(periodEnd) ? periodEnd : potentialEnd;
            } else {
                // 다음 이력이 없으면(현재 기기의 마지막 이력) 월말까지
                segmentEnd = periodEnd;
            }

            // 유효하지 않은 세그먼트(시작 > 종료)는 스킵 (예: 다음달 시작 건이 미리 들어온 경우 등)
            if (segmentStart.isAfter(segmentEnd)) {
                continue;
            }

            resultSegments.add(SubscriptionSegmentDto.builder()
                    .usersId(usersId)
                    .deviceId(current.getDeviceId())
                    .subscribeBillingHistoryId(current.getSubscribeBillingHistoryId())
                    .subscribeServiceId(current.getSubscribeServiceId())
                    .serviceName(current.getServiceName())
                    .segmentStartDate(segmentStart)
                    .segmentEndDate(segmentEnd)
                    .originAmount(current.getOriginAmount())
                    .discountAmount(current.getDiscountAmount())
                    .totalAmount(current.getTotalAmount())
                    .build());
        }
    }
}
