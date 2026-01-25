package com.mycom.myapp.sendapp.delivery.scheduler;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelayedTransferer {
	
	private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    
    public void transfer() {
        RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(DELAY_ZSET, StringCodec.INSTANCE);
        long now = System.currentTimeMillis();
        Collection<String> expiredMessages = zset.valueRange(0, true, now, true);
        
        if (expiredMessages.isEmpty()) return;

        log.info("지연 큐에서 {}건 발견, Stream으로 이동 시작", expiredMessages.size());

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Object msg : expiredMessages) { // String 대신 Object로 받아 확인
                try {
                    String jsonPayload = String.valueOf(msg); // 명시적 변환
                    
                    // 🔍 만약 데이터 자체가 "text"라는 문자열로 들어온다면 skip
                    if ("text".equals(jsonPayload)) continue;

                    Map<String, String> dataMap = objectMapper.readValue(jsonPayload, new TypeReference<Map<String, String>>() {});
                    
                    MapRecord<String, String, String> record = StreamRecords.newRecord()
                            .in(WAITING_STREAM)
                            .ofMap(dataMap);

                    stringRedisTemplate.opsForStream().add(record);
                } catch (Exception e) {
                    log.error("Transfer 실패 - Record: {}, Error: {}", msg, e.getMessage());
                }
            }
            return null;
        });

        zset.removeRangeByScore(0, true, now, true);
        log.info("{}건 이동 및 지연 큐 청소 완료", expiredMessages.size());
    }
}
