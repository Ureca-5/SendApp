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
                fieldMap.put("email", dto.getEmail());
                fieldMap.put("phone", dto.getPhone());
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
        
        log.info("✅ [재발송] {}건 Redis 대기열 적재 완료", failedList.size());
    }
 // [Fallback 스케줄러] 이메일 3번 실패하면 SMS로 전환 (10초마다 체크)
    @Scheduled(cron = "*/10 * * * * *") 
    @Transactional
    public void fallbackToSms() {
        // 1. 3번 이상 실패한 이메일 건 조회 (폰번호 들고옴)
        List<DeliveryRetryDto> fallbackList = statusRepository.findFallbackTargets(MAX_RETRY_COUNT);

        if (fallbackList.isEmpty()) {
            return;
        }

        log.info("🚨 [채널 전환] 이메일 발송 실패 {}건 -> SMS로 전환 시도", fallbackList.size());

        for (DeliveryRetryDto dto : fallbackList) {
            try {
                // 2. Redis 메시지 생성 (이미 DTO에 SMS, 폰번호가 들어있음)
                Map<String, String> fieldMap = new HashMap<>();
                fieldMap.put("invoice_id", String.valueOf(dto.getInvoiceId()));
                fieldMap.put("delivery_channel", dto.getDeliveryChannel()); // "SMS"
                
                // 로그 확인용: "SMS 1회차"라고 보이게 1을 넣음 (DB는 0으로 초기화됨)
                fieldMap.put("retry_count", "1"); 
                fieldMap.put("email", dto.getEmail());
                fieldMap.put("phone", dto.getPhone());
                fieldMap.put("billing_yyyymm", dto.getBillingYyyymm());
                fieldMap.put("recipient_name", dto.getRecipientName());
                fieldMap.put("receiver_info", dto.getReceiverInfo()); // 폰번호 (010-xxxx)
                fieldMap.put("total_amount", String.valueOf(dto.getTotalAmount()));

                // 3. Redis 적재
                MapRecord<String, String, String> record = StreamRecords.mapBacked(fieldMap).withStreamKey(WAITING_STREAM);
                redisTemplate.opsForStream().add(record);

                // 4. DB 업데이트 (채널 SMS로 변경, 카운트 0으로 초기화)
                statusRepository.switchToSms(dto.getInvoiceId());

            } catch (Exception e) {
                log.error("❌ SMS 전환 실패 (ID: {})", dto.getInvoiceId(), e);
            }
        }
        log.info("✅ [채널 전환] {}건 SMS 대기열 적재 완료", fallbackList.size());
    }
}