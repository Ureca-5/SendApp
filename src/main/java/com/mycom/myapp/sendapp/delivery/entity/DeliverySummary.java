package com.mycom.myapp.sendapp.delivery.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySummary {
    private Long deliverySummaryId;
    private Integer billingYyyymm;    // 청구 년월 (UK)
    private DeliveryChannelType deliveryChannel;   // 배송 채널 (UK)
    private Integer totalAttemptCount;
    private Integer successCount;
    private Integer failCount;
    private BigDecimal successRate;   // DECIMAL(5,2) -> BigDecimal
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;  // Nullable
}
