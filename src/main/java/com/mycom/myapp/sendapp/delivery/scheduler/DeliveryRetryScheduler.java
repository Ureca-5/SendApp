package com.mycom.myapp.sendapp.delivery.scheduler;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.DELAY_ZSET;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.api.RBatch;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycom.myapp.sendapp.delivery.dto.DeliveryRetryDto;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryRetryScheduler {

    private final DeliveryStatusRepository statusRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 2;
    private static final int BAN_START_HOUR = 21; 
    private static final int BAN_END_HOUR = 9;    

    // ♻️ [재발송] 10초마다
    @Scheduled(cron = "*/10 * * * * *") 
    @Transactional
    public void retryFailedDeliveries() {
        List<DeliveryRetryDto> failedList = statusRepository.findRetryTargets(MAX_RETRY_COUNT);
        if (failedList.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = adjustForBusinessHours(now);
        boolean isNightBan = targetTime.isAfter(now);

        // [CASE 1] 야간 금지 -> 내일 아침으로 연기
        if (isNightBan) {
            List<Long> ids = failedList.stream().map(DeliveryRetryDto::getInvoiceId).toList();
            statusRepository.postponeDelivery(ids, targetTime);
            log.info("🌙 [재발송 제한] 야간이라 {}건을 내일 아침({})으로 연기", ids.size(), targetTime);
            return;
        }

        // [CASE 2] 업무 시간 -> Redis Batch 적재
        RBatch batch = redissonClient.createBatch();
        RScoredSortedSetAsync<String> batchZset = batch.getScoredSortedSet(DELAY_ZSET, StringCodec.INSTANCE);
        long delayUntil = System.currentTimeMillis() + 1000;

        for (DeliveryRetryDto dto : failedList) {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("invoice_id", String.valueOf(dto.getInvoiceId()));
                payload.put("delivery_channel", dto.getDeliveryChannel());
                payload.put("retry_count", String.valueOf(dto.getRetryCount() + 1));
                payload.put("email", dto.getEmail());
                payload.put("phone", dto.getPhone());
                payload.put("recipient_name", dto.getRecipientName());
                payload.put("total_amount", String.valueOf(dto.getTotalAmount()));
                payload.put("requested_at", now.toString());

                batchZset.addAsync(delayUntil, objectMapper.writeValueAsString(payload));
                statusRepository.resetStatusToReady(dto.getInvoiceId(), dto.getRetryCount());
            } catch (Exception e) {
                log.error("❌ 재발송 실패 (ID: {})", dto.getInvoiceId());
            }
        }
        batch.execute();
        log.info("✅ [재발송] {}건 Redis 적재 완료", failedList.size());
    }

    // 🚨 [Fallback] 이메일 실패 -> SMS 전환
    @Scheduled(cron = "*/10 * * * * *") 
    @Transactional
    public void fallbackToSms() {
        List<DeliveryRetryDto> fallbackList = statusRepository.findFallbackTargets(MAX_RETRY_COUNT);
        if (fallbackList.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = adjustForBusinessHours(now);
        boolean isNightBan = targetTime.isAfter(now);

        if (isNightBan) {
            List<Long> ids = fallbackList.stream().map(DeliveryRetryDto::getInvoiceId).toList();
            statusRepository.postponeDelivery(ids, targetTime);
            log.info("🌙 [SMS전환 제한] 야간이라 {}건을 내일 아침으로 연기", ids.size());
            return;
        }

        RBatch batch = redissonClient.createBatch();
        RScoredSortedSetAsync<String> batchZset = batch.getScoredSortedSet(DELAY_ZSET, StringCodec.INSTANCE);
        long delayUntil = System.currentTimeMillis() + 1000;

        for (DeliveryRetryDto dto : fallbackList) {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("invoice_id", String.valueOf(dto.getInvoiceId()));
                payload.put("delivery_channel", "SMS");
                payload.put("retry_count", "0");
                payload.put("email", dto.getEmail());
                payload.put("phone", dto.getPhone());
                payload.put("recipient_name", dto.getRecipientName());
                payload.put("total_amount", String.valueOf(dto.getTotalAmount()));
                payload.put("requested_at", now.toString());

                batchZset.addAsync(delayUntil, objectMapper.writeValueAsString(payload));
                statusRepository.switchToSms(dto.getInvoiceId());
            } catch (Exception e) {
                log.error("❌ SMS 전환 실패 (ID: {})", dto.getInvoiceId());
            }
        }
        batch.execute();
        log.info("✅ [SMS 전환] {}건 Redis 적재 완료", fallbackList.size());
    }

    private LocalDateTime adjustForBusinessHours(LocalDateTime targetTime) {
        int hour = targetTime.getHour();
        if (hour >= BAN_START_HOUR) {
            return targetTime.plusDays(1).withHour(BAN_END_HOUR).withMinute(0).withSecond(0);
        }
        if (hour < BAN_END_HOUR) {
            return targetTime.withHour(BAN_END_HOUR).withMinute(0).withSecond(0);
        }
        return targetTime;
    }
}