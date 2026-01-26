package com.mycom.myapp.sendapp.batch.dto;


import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 단건 결제 원천 데이터 조회 결과 매핑 DTO
 * - 모든 필드는 wrapper 타입 사용 (nullable 대응)
 */
@Getter
@Builder
public class MicroPaymentBillingHistoryRowDto {
    private Long microPaymentBillingHistoryId;
    private Long usersId;

    private Integer billingYyyymm;

    /** 서비스/상품 식별자(있다면) */
    private Long microPaymentServiceId;

    /** 서비스명 스냅샷 */
    private String serviceName;

    /** 원금/할인/최종 */
    private Long originAmount;
    private Long discountAmount;
    private Long totalAmount;

    /** 생성 시각 */
    private LocalDateTime createdAt;
}
