package com.mycom.myapp.sendapp.delivery.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUser {
    // DB의 users 테이블 컬럼과 매핑될 필드들
    private Long usersId;        // users_id
    private String name;        // name
    private String email;       // email
    private String phone;       // phone
    private Boolean isWithdrawn;// is_withdrawn (필요 시 사용)
    private Integer preferredHour; //선호 시간
    private Integer preferredDay; //선호 발송일
}