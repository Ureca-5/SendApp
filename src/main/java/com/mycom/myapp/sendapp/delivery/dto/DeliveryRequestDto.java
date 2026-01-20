package com.mycom.myapp.sendapp.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRequestDto {
	private String eventType;   // "BILLING_CREATED"
    private Long invoiceId;     // 나중에 '상세보기' 링크 만들 때 사용

    private Recipient recipient; // 받는 사람 정보
    private BillSummary summary; // 금액 요약 정보

    @Data @Builder
    public static class Recipient {     //유저정보만
        private String name;
        private String email;
        private String phone;
    }

    @Data @Builder
    public static class BillSummary {   //돈이랑 날짜정보만
        private String billingYyyymm;   // "2026년 1월"로 포맷팅 예정
        private String issueDate;       // "2026-01-16"
        
        // 금액들은 3자리 콤마(,) 포맷팅해서 문자열로 저장하는 게 편함
        private String totalAmount;     
        private String planAmount;
        private String addonAmount;
        private String etcAmount;
        private String discountAmount;
    }
}
