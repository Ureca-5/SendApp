package com.mycom.myapp.sendapp.batch.service.settlement;

import com.mycom.myapp.sendapp.batch.calculator.SubscriptionSegmentCalculator;
import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceDetailRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;
import com.mycom.myapp.sendapp.batch.dto.SubscriptionSegmentDto;
import com.mycom.myapp.sendapp.batch.repository.subscribe.SubscribeBillingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 역할:
 * - 청크(usersIds)의 구독 원천 데이터를 "한 번에" 조회
 * - SubscriptionSegmentCalculator로 세그먼트 확정
 * - 세그먼트를 MonthlyInvoiceDetailRowDto로 변환하여 반환
 *
 * 설계 근거:
 * - Repository에서 users_id, billing_yyyymm, device_id 기준 정렬 보장 -> Map/정렬 비용 최소화 가능
 * - Chunk 단위 조회 1회로 DB round-trip 최소화
 */
@RequiredArgsConstructor
@Service
public class SubscribeSettlementServiceImpl implements SubscribeSettlementService {
    private final SubscribeBillingHistoryRepository billingHistoryRepository;
    private final SubscriptionSegmentCalculator segmentCalculator;

    @Override
    public List<MonthlyInvoiceDetailRowDto> settle(Integer targetYyyymm, List<Long> usersIds) {
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("targetYyyymm is required.");
        }
        if (usersIds == null || usersIds.isEmpty()) {
            return List.of();
        }

        // 1) 청크 단위 전체 유저의 구독 원천 데이터를 "한 번에" 조회
        //    (Repository에서 ORDER BY users_id, billing_yyyymm, device_id 보장)
        List<SubscribeBillingHistoryRowDto> rawRows =
                billingHistoryRepository.findByUsersIdsAndYyyymm(targetYyyymm, usersIds);

        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }

        // 2) 세그먼트 확정 (정렬된 리스트를 전제로 계산)
        List<SubscriptionSegmentDto> segments = segmentCalculator.calculate(targetYyyymm, rawRows);
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        // 3) 세그먼트 -> 청구서 상세 DTO 변환
        LocalDateTime now = LocalDateTime.now();
        List<MonthlyInvoiceDetailRowDto> details = new ArrayList<>(segments.size());

        for (SubscriptionSegmentDto s : segments) {
            // invoiceId는 Writer에서 주입해야 하므로 null
            details.add(MonthlyInvoiceDetailRowDto.builder()
                    .detailId(null)
                    .invoiceId(null)
                    .invoiceCategoryId(null) // TODO: subscribe_service_category_id → invoice_category_id 매핑 확정 후 주입
                    .billingHistoryId(s.getSubscribeBillingHistoryId())
                    .serviceName(s.getServiceName())
                    .originAmount(s.getOriginAmount())
                    .discountAmount(s.getDiscountAmount())
                    .totalAmount(s.getTotalAmount())
                    .usageStartDate(s.getSegmentStartDate())
                    .usageEndDate(s.getSegmentEndDate())
                    .createdAt(now)
                    .expiredAt(now.toLocalDate().plusYears(5))
                    .build()
            );
        }

        return details;
    }
}
