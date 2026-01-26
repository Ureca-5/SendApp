package com.mycom.myapp.sendapp.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUserDto {
    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Integer preferredHour;
}