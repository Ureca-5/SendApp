package com.mycom.myapp.sendapp.delivery.config;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamBatchConfig {

    private final StringRedisTemplate redisTemplate;
    

    @Bean
    public ApplicationRunner initializeStreamAndGroup() {
        return args -> {
            try {
            	
                redisTemplate.opsForStream().createGroup(WAITING_STREAM, ReadOffset.from("0-0"), GROUP_NAME);
                log.info(">>> [{}] 소비자 그룹이 생성되었습니다. (Target: {})", GROUP_NAME, WAITING_STREAM);

            } catch (Exception e) {
                String message = e.getMessage();
                if (message != null && message.contains("BUSYGROUP")) {
                    log.info(">>> 소비자 그룹이 이미 존재합니다.");
                } else if (message != null && message.contains("NOKEY")) {
                    // 스트림 키가 없어서 발생한 경우, 더미 데이터를 하나 넣어서 스트림을 강제 생성하고 그룹을 만듭니다.
                    log.info(">>> 스트림이 없어 강제 생성 후 그룹을 설정합니다.");
                    redisTemplate.opsForStream().add(WAITING_STREAM, Collections.singletonMap("status", "init"));
                    redisTemplate.opsForStream().createGroup(WAITING_STREAM, ReadOffset.from("0-0"), GROUP_NAME);
                } else {
                    log.warn(">>> 소비자 그룹 초기화 중 알 수 없는 상태: {}", message);
                }
            }
        };
    }
}