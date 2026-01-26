package com.mycom.myapp.sendapp.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBillingDayDto {
    private Long usersId;
    private Integer billingDay; // 일자(1~31 예상), 말일보다 크면 Processor에서 말일로 보정
}
