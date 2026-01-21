package com.mycom.myapp.sendapp.delivery.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class DeliveryRetryDto {
    private Long invoiceId;
    private String deliveryChannel;
    private int retryCount;
    
    // 조인해서 가져올 정보들
    private String billingYyyymm;
    private Long totalAmount;
    private String recipientName; // delivery_user.name
    private String receiverInfo;  // delivery_user.email (or phone)
}