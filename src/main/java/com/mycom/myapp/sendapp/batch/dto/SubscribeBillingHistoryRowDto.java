package com.mycom.myapp.sendapp.batch.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class SubscribeBillingHistoryRowDto {
    private Long subscribeBillingHistoryId;
    private Long usersId;
    private Long deviceId;
    private Integer subscribeServiceId;
    private Integer subscribeCategoryId;
    private String serviceName;
    private LocalDate subscriptionStartDate;
    private Long originAmount;
    private Long discountAmount;
    private Long totalAmount;
    private Integer billingYyyymm;
}
