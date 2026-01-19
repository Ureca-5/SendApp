package com.mycom.myapp.sendapp.delivery.config;

import com.mycom.myapp.sendapp.delivery.service.DeliveryWorker;
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
    
    // 엔진 역할을 하는 컨테이너 빈 등록
    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory factory) {

    	StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        var container = StreamMessageListenerContainer.create(factory, options);
        container.start(); 
        return container;
    }
    
    // 설정을 담당하는 구독 빈 등록
    @Bean
    public Subscription subscription(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
    	
        // 1. 소비자 그룹 생성 시도
    	try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
        } catch (RedisSystemException e) {
            log.info("Consumer Group already exists or Stream key not created yet.");
        }
    	
        // 2. 소비자 설정 (그룹명, 워커명)
    	Consumer consumer = Consumer.from(GROUP_NAME, "worker-1");
        
        // 3. 읽기 오프셋 설정 (마지막 소비 시점 이후부터)
        StreamOffset<String> offset = StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed());

        // 4. 컨테이너에 리스너 연결 및 구독 시작
        Subscription subscription = container.receive(consumer, offset, deliveryWorker);
        
        log.info(">>> Redis Stream Subscription 활성화: {}", STREAM_KEY);
        return subscription;
    }
}