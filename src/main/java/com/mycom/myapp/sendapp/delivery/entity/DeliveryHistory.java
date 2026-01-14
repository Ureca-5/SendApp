package com.mycom.myapp.sendapp.delivery.entity;

import java.time.LocalDateTime;

import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryResultType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryHistory {
    private Long deliveryHistoryId;
    private Long invoiceId;         // FK: monthly_invoice table
    private Integer attemptNo;      // 시도 횟수
    private DeliveryChannelType deliveryChannel;
    private String receiverInfo;    // 마스킹 처리된 정보
    private DeliveryResultType status;          // SUCCESS/FAIL
    private String errorMessage;    // Nullable
    private LocalDateTime requestedAt; // Nullable
    private LocalDateTime sentAt;      // Nullable
}