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

//    @Bean
//    public Subscription subscription(RedisConnectionFactory factory) {
//    	
//    	try {
//            redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
//        } catch (RedisSystemException e) {
//            log.info("Consumer Group already exists or Stream key not created yet.");
//        }
//    	
//        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
//                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
//                        .builder()
//                        .pollTimeout(Duration.ofSeconds(1)) 
//                        .build();
//
//        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
//                StreamMessageListenerContainer.create(factory, options);
//
//        Consumer consumer = Consumer.from(GROUP_NAME, "worker-1");
//
//
//        StreamOffset<String> offset = StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed());
//
//
//        Subscription subscription = container.receive(consumer, offset, deliveryWorker);
//
//        container.start();
//        
//        log.info(">>> Redis Stream Subscription Started: {} group", GROUP_NAME);
//        return subscription;
//    }
}