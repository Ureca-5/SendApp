package com.mycom.myapp.sendapp.delivery.scheduler;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.WAITING_STREAM;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryRetryDto; // ★ DTO 재사용
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryScheduledWorker {

    private final DeliveryStatusRepository statusRepository;
    private final StringRedisTemplate stringRedisTemplate;

    // 1분마다 실행 (예약 시간 도래 체크)
    @Scheduled(cron = "0 * * * * *")
    public void processScheduled() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 시간이 된 예약 건 조회
        List<DeliveryRetryDto> targets = statusRepository.findScheduledTargets(now);

        if (targets.isEmpty()) return;

        log.info("⏰ 예약 발송 대상 {}건 발견. Redis 이관 시작...", targets.size());

        try {
            // 2. Redis Pipeline 적재
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (DeliveryRetryDto target : targets) {
                    
                    // Loader와 동일한 Map 구조 생성
                    Map<String, String> streamMap = new HashMap<>();
                    streamMap.put("invoice_id", String.valueOf(target.getInvoiceId()));
                    streamMap.put("delivery_channel", "EMAIL");
                    streamMap.put("retry_count", "0");
                    streamMap.put("email", target.getEmail());
                    streamMap.put("phone", target.getPhone());
                    streamMap.put("recipient_name", target.getRecipientName());
                    streamMap.put("billing_yyyymm", target.getBillingYyyymm());
                    streamMap.put("total_amount", String.valueOf(target.getTotalAmount()));
                    streamMap.put("requested_at", now.toString());

                    MapRecord<String, String, String> record = StreamRecords.newRecord()
                            .in(WAITING_STREAM).ofMap(streamMap);
                    
                    stringRedisTemplate.opsForStream().add(record);
                }
                return null;
            });

            // 3. DB 상태 변경 (SCHEDULED -> READY)
            List<Long> ids = targets.stream().map(DeliveryRetryDto::getInvoiceId).toList();
            statusRepository.updateStatusToReadyBatch(ids);

            log.info("✅ 예약 발송 {}건 처리 완료 (SCHEDULED -> READY)", targets.size());

        } catch (Exception e) {
            log.error("❌ 예약 발송 Redis 이관 중 실패", e);
        }
    }
}