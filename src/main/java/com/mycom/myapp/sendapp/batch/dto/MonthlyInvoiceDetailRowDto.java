package com.mycom.myapp.sendapp.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class MonthlyInvoiceDetailRowDto {
    /** 상세 식별자 */
    private Long detailId;

    /** 청구서 헤더 식별자 (FK) */
    private Long invoiceId;

    /** 청구 항목 카테고리 식별자 (요금제/부가서비스/ETC) */
    private Integer invoiceCategoryId;

    /**
     * 원천 데이터 식별자
     * - subscribe_billing_history_id
     * - micro_payment_billing_history_id
     */
    private Long billingHistoryId;

    /** 서비스명 스냅샷 */
    private String serviceName;

    /** 원금 */
    private Long originAmount;

    /** 할인 금액 */
    private Long discountAmount;

    /** 최종 금액 */
    private Long totalAmount;

    /** 사용 시작 시각 */
    private LocalDate usageStartDate;

    /** 사용 종료 시각 */
    private LocalDate usageEndDate;

    /** 생성 시각 */
    private LocalDateTime createdAt;

    /** 만료 시각 */
    private LocalDate expiredAt;
}
