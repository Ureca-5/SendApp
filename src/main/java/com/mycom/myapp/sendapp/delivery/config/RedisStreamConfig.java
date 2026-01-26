//package com.mycom.myapp.sendapp.delivery.config;
//
//import com.mycom.myapp.sendapp.delivery.service.DeliveryWorker;
//import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.*;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.RedisSystemException;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.stream.*;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.stream.StreamMessageListenerContainer;
//import org.springframework.data.redis.stream.Subscription;
//
//import java.time.Duration;
//
//@Slf4j
//@Configuration
//@RequiredArgsConstructor
//public class RedisStreamConfig {
//
//    private final DeliveryWorker deliveryWorker;
//    private final StringRedisTemplate redisTemplate;
//    
//    // 엔진 역할을 하는 컨테이너 빈 등록
//    @Bean(destroyMethod = "stop")
//    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
//            RedisConnectionFactory factory) {
//
//    	StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
//                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
//                        .builder()
//                        .pollTimeout(Duration.ofSeconds(1))
//                        .build();
//
//        var container = StreamMessageListenerContainer.create(factory, options);
//        container.start(); 
//        return container;
//    }
//    
//    // 설정을 담당하는 구독 빈 등록
//    @Bean
//    public Subscription subscription(StreamMessageListenerContainer<String, MapRecord<String, String, String>> container) {
//    	
//    	try {
//            redisTemplate.opsForStream().createGroup(WAITING_STREAM, ReadOffset.from("0-0"), GROUP_NAME);
//        } catch (RedisSystemException e) {
//            log.info(">>> 소비자 그룹이 이미 존재하거나 스트림이 준비되지 않았습니다.");
//        }
//    	
////    	createGroupIfNotExists(); // 그룹 생성 방어 로직
//    	
//    	Consumer consumer = Consumer.from(GROUP_NAME, "worker-1");
//    	StreamOffset<String> offset = StreamOffset.create(WAITING_STREAM, ReadOffset.lastConsumed());
//    	
//    	Subscription subscription = container.receive(consumer, offset, deliveryWorker);
//    	
//    	log.info("Redis Stream Listener 가동 [Stream: {}, Group: {}]", WAITING_STREAM, GROUP_NAME);
//        
//        return subscription;
//    }
//}