package com.mycom.myapp.sendapp.delivery.worker.util;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final StringRedisTemplate redisTemplate;
    private static final String DONE_KEY_PREFIX = "send:done:";

    /**
     * 발송 가능 여부 확인
     * @return true면 이미 발송 완료된 건(차단), false면 발송 대상
     */
    public boolean isAlreadySent(Long invoiceId) {
        String key = DONE_KEY_PREFIX + invoiceId;
        String val = redisTemplate.opsForValue().get(key);
        
        // Redis에 "SENT"라고 기록되어 있으면 어떤 경우에도 재발송 금지
        return "SENT".equals(val);
    }

    public void markAsSent(Long invoiceId) {
        String key = DONE_KEY_PREFIX + invoiceId;

        redisTemplate.opsForValue().set(key, "SENT", Duration.ofDays(1));
    }
    
    // [테스트용] 특정 청구서의 멱등성 초기화
    public void clear(Long invoiceId) {
        redisTemplate.delete(DONE_KEY_PREFIX + invoiceId);
    }
}
