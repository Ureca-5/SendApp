package com.mycom.myapp.sendapp.delivery.scheduler;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.DELAY_ZSET;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class DeliveryScheduledWorker {

    private final DeliveryStatusRepository statusRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final int BAN_START_HOUR = 21; 
    private static final int BAN_END_HOUR = 9;  

    // ⏰ 1분마다 실행
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processScheduled() {
        LocalDateTime now = LocalDateTime.now();
        List<DeliveryRetryDto> targets = statusRepository.findScheduledTargets(now);
        if (targets.isEmpty()) return;

        // ★ 안전장치: 혹시라도 지금이 금지 시간대라면 다시 미뤄야 함
        LocalDateTime adjustedTime = adjustForBusinessHours(now);
        if (adjustedTime.isAfter(now)) {
            List<Long> ids = targets.stream().map(DeliveryRetryDto::getInvoiceId).toList();
            statusRepository.postponeDelivery(ids, adjustedTime);
            log.warn("⏰ [예약 보호] 야간({})이라 {}건을 다시 내일 아침으로 연기", now, ids.size());
            return;
        }

        RBatch batch = redissonClient.createBatch();
        RScoredSortedSetAsync<String> batchZset = batch.getScoredSortedSet(DELAY_ZSET, StringCodec.INSTANCE);
        long delayUntil = System.currentTimeMillis() + 1000;
        
        List<Long> processedIds = new ArrayList<>();

        for (DeliveryRetryDto target : targets) {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("invoice_id", String.valueOf(target.getInvoiceId()));
                payload.put("delivery_channel", "EMAIL");
                payload.put("retry_count", "0");
                payload.put("email", target.getEmail());
                payload.put("phone", target.getPhone());
                payload.put("recipient_name", target.getRecipientName());
                payload.put("total_amount", String.valueOf(target.getTotalAmount()));
                payload.put("requested_at", now.toString());

                batchZset.addAsync(delayUntil, objectMapper.writeValueAsString(payload));
                processedIds.add(target.getInvoiceId());
            } catch (Exception e) {
                log.error("❌ 예약 건 처리 실패 (ID: {})", target.getInvoiceId());
            }
        }

        if (!processedIds.isEmpty()) {
            batch.execute();
            statusRepository.updateStatusToReadyBatch(processedIds);
            log.info("✅ 예약 발송 {}건 Redis 이관 완료", processedIds.size());
        }
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