package com.mycom.myapp.sendapp.delivery.config;

import com.mycom.myapp.sendapp.delivery.service.DeliveryWorker; // 이후 생성할 서비스
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final DeliveryWorker deliveryWorker;
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_KEY = "billing:delivery:waiting";
    private static final String GROUP_NAME = "delivery-group";

    @Bean
    public Subscription subscription(RedisConnectionFactory factory) {
        // 1. StreamMessageListenerContainer 옵션 설정
        // 이 컨테이너가 Redis를 감시하다가 이벤트가 발생하면 Listener에게 던져줍니다.
    	
    	try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
        } catch (RedisSystemException e) {
            // 이미 그룹이 있는 경우 발생하는 예외이므로 무시해도 됨
            log.info("Consumer Group already exists or Stream key not created yet.");
        }
    	
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(1)) // 이벤트가 없을 때 대기 시간
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        // 2. 소비자(Consumer) 설정
        // Consumer Group 내에서 'worker-1'이라는 이름으로 활동합니다.
        Consumer consumer = Consumer.from(GROUP_NAME, "worker-1");

        // 3. 읽기 오프셋 설정
        // > 기호는 "아직 읽지 않은 메시지만 가져오겠다"는 트리거의 핵심 설정입니다.
        StreamOffset<String> offset = StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed());

        // 4. 컨테이너에 리스너(DeliveryWorker) 등록
        Subscription subscription = container.receive(consumer, offset, deliveryWorker);

        // 5. 컨테이너 구동 시작
        container.start();
        
        log.info(">>> Redis Stream Subscription Started: {} group", GROUP_NAME);
        return subscription;
    }
}