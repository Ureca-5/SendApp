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
    // 배송에 필요한 정보만 딱 정의합니다. (joinedAt 등 불필요한 필드 제거)
}