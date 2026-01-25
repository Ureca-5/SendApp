package com.mycom.myapp.sendapp.delivery.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DeliveryRedisKey {
	
	// 기존 stream용 키
	public static final String WAITING_STREAM = "billing:delivery:waiting";
	public static final String FAILED_STREAM = "billing:delivery:failed";
	
	// ZSET용 키
	public static final String DELAY_ZSET = "billing:delivery:delayed";
	
    public static final String GROUP_NAME = "delivery-group";
}
