package com.mycom.myapp.sendapp.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class SubscriptionSegmentDto {
    private Long usersId;
    private Long deviceId;
    private Long subscribeBillingHistoryId;
    private Integer subscribeServiceId;
    private String serviceName;

    private LocalDate segmentStartDate;
    private LocalDate segmentEndDate;

    private Long originAmount;
    private Long discountAmount;
    private Long totalAmount;
}
