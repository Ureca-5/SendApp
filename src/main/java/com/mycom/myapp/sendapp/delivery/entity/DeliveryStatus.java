package com.mycom.myapp.sendapp.delivery.entity;

import java.time.LocalDateTime;

import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatus {
    private Long deliveryStatusId;
    private Long invoiceId;         // FK: monthly_invoice table
    private DeliveryStatusType status;          // 배송 상태
    private DeliveryChannelType deliveryChannel; // 배송 채널 (SMS, EMAIL 등)
    private Integer retryCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt; //예약 발송 시간 (예약 아니면 NULL)
}