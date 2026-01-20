package com.mycom.myapp.sendapp.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key는 String으로 저장 (예: "billing:send:stream")
        template.setKeySerializer(new StringRedisSerializer());
        
        // Value는 JSON으로 저장 (DTO -> JSON 자동 변환)
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        // Hash Key/Value도 JSON으로 (Stream은 Hash 구조랑 유사함)
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}