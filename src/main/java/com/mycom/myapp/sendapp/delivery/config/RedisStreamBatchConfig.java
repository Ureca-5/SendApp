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

    /**
     * 리스너 컨테이너(Push 방식) 대신 사용.
     * 애플리케이션 시작 시 스트림과 소비자 그룹을 생성하여 배치 워커가 활동할 기반을 만듭니다.
     */
    @Bean
    public ApplicationRunner initializeStreamAndGroup() {
        return args -> {
            try {
            	
                redisTemplate.opsForStream().createGroup(WAITING_STREAM, ReadOffset.from("0-0"), GROUP_NAME);
                log.info(">>> [{}] 소비자 그룹이 생성되었습니다. (Target: {})", GROUP_NAME, WAITING_STREAM);

            } catch (Exception e) {
                // BusyGroupException 등 이미 그룹이 존재하는 경우 무시합니다.
                log.info(">>> 소비자 그룹이 이미 존재하거나 스트림이 준비되었습니다.");
            }
        };
    }
}