package com.mycom.myapp.sendapp.batch.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class MonthlyInvoiceRowDto implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 식별자 (DB insert 이후 채워질 수 있음) */
    private Long invoiceId;

    /** 회원 식별자 */
    private Long usersId;

    /** 정산 대상 월 (YYYYMM) */
    private Integer billingYyyymm;

    /** 요금제 합계 */
    private Long totalPlanAmount;

    /** 부가서비스 합계 */
    private Long totalAddonAmount;

    /** 기타(단건 결제 등) 합계 */
    private Long totalEtcAmount;

    /** 전체 할인 금액 */
    private Long totalDiscountAmount;

    /** 최종 청구 금액 */
    private Long totalAmount;

    /** 납부 기한 */
    private LocalDate dueDate;

    /** 생성 시각 */
    private LocalDateTime createdAt;

    /** 만료 시각 */
    private LocalDate expiredAt;

    /** 정산 성공 여부 **/
    private Boolean settlementSuccess;
}
