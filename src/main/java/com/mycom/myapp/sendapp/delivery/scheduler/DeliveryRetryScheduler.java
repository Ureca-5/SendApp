package com.mycom.myapp.sendapp.delivery.scheduler;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryRetryDto; 
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.WAITING_STREAM;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryRetryScheduler {

    private final DeliveryStatusRepository statusRepository;
    private final StringRedisTemplate redisTemplate;
    
    private static final int MAX_RETRY_COUNT = 3; 

    // ★ 수정됨: "/10" -> "*/10" (10초마다) 또는 "0 * * * * *" (1분마다)
    @Scheduled(cron = "*/10 * * * * *") 
    @Transactional
    public void retryFailedDeliveries() {
        // 1. DTO로 조회 (JOIN된 데이터)
        List<DeliveryRetryDto> failedList = statusRepository.findRetryTargets(MAX_RETRY_COUNT);

        if (failedList.isEmpty()) {
            return;
        }

        log.info("♻️ [재발송] 대상 {}건 발견. 복구 시작...", failedList.size());

        for (DeliveryRetryDto dto : failedList) {
            try {
                // 2. Redis 메시지 생성
                Map<String, String> fieldMap = new HashMap<>();
                fieldMap.put("invoice_id", String.valueOf(dto.getInvoiceId()));
                fieldMap.put("delivery_channel", dto.getDeliveryChannel());
                // 로그 확인용으로 +1 된 값을 Redis에 보냄
                fieldMap.put("retry_count", String.valueOf(dto.getRetryCount() + 1)); 
                
                fieldMap.put("billing_yyyymm", dto.getBillingYyyymm());
                fieldMap.put("recipient_name", dto.getRecipientName());
                fieldMap.put("receiver_info", dto.getReceiverInfo());
                fieldMap.put("total_amount", String.valueOf(dto.getTotalAmount()));

                // 3. Redis 적재
                MapRecord<String, String, String> record = StreamRecords.mapBacked(fieldMap).withStreamKey(WAITING_STREAM);
                redisTemplate.opsForStream().add(record);

                // 4. DB 업데이트 (READY로 변경, 카운트 증가)
                statusRepository.resetStatusToReady(dto.getInvoiceId(), dto.getRetryCount());

            } catch (Exception e) {
                log.error("❌ 재발송 실패 (ID: {})", dto.getInvoiceId(), e);
            }
        }
        
        log.info("✅ [재발송] {}건 처리 완료", failedList.size());
    }
}