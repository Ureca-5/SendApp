package com.mycom.myapp.sendapp.delivery.dto;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeliveryPayload {
    private final Long invoiceId;
    private final String channel;
    private final String recipientName;
    private final String encEmail; // 암호화된 상태
    private final String endphone;
    private final String billingYyyymm;
    private final String totalAmount; // 총 청구금액
    private final LocalDateTime requestedAt;
    private final int attemptNo;
    
    /** 요금제 합계 */
    private final String totalPlanAmount;

    /** 부가서비스 합계 */
    private final String totalAddonAmount;

    /** 기타(단건 결제 등) 합계 */
    private final String totalEtcAmount;

    /** 전체 할인 금액 */
    private final String totalDiscountAmount;

    /** 납부 기한 (표시용 포맷된 문자열) */
    private final String dueDate;
    

    // Map -> DTO
    public static DeliveryPayload from(Map<String, String> map) {
        try {
            return DeliveryPayload.builder()
                    .invoiceId(map.get("invoice_id") != null ? Long.valueOf(map.get("invoice_id").trim()) : 0L)
                    .channel(map.get("delivery_channel"))
                    .recipientName(map.get("recipient_name"))
                    .encEmail(map.get("email"))
                    .endphone(map.get("phone"))
                    .billingYyyymm(map.get("billing_yyyymm"))
                    .totalAmount(map.get("total_amount"))
                    // 날짜 파싱 실패 방지를 위한 방어 로직
                    .requestedAt(map.get("requested_at") != null ? 
                                 LocalDateTime.parse(map.get("requested_at").trim()) : LocalDateTime.now())
                    .attemptNo(Integer.parseInt(map.getOrDefault("retry_count", "0").trim()) + 1)
                    
                    // 상세 요금 필드 (Redis 키값과 DTO 필드명이 일치하므로 get 사용)
                    .totalPlanAmount(map.getOrDefault("totalPlanAmount", "0"))
                    .totalAddonAmount(map.getOrDefault("totalAddonAmount", "0"))
                    .totalEtcAmount(map.getOrDefault("totalEtcAmount", "0"))
                    .totalDiscountAmount(map.getOrDefault("totalDiscountAmount", "0"))
                    .dueDate(map.getOrDefault("dueDate", "-"))
                    .build();
        } catch (Exception e) {
            // 여기서 로그를 찍으면 정확히 어떤 데이터의 어떤 필드에서 터졌는지 알 수 있습니다.
            throw new IllegalArgumentException("DTO 변환 실패 - 데이터: " + map + ", 에러: " + e.getMessage());
        }
    }
}
