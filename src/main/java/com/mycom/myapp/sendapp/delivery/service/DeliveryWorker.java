package com.mycom.myapp.sendapp.delivery.service;

import com.mycom.myapp.sendapp.delivery.entity.DeliveryHistory;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryResultType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryHistoryRepository;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryWorker implements StreamListener<String, MapRecord<String, String, String>> {

    private final DeliveryStatusRepository statusRepository;
    private final DeliveryHistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        // 1. 트리거 발동 - 메시지 수신
        Map<String, String> body = record.getValue();
        
        // Redis JSON 데이터에서 필요한 정보 추출
        Long statusId = Long.valueOf(body.get("delivery_status_id"));
        Long invoiceId = Long.valueOf(body.get("invoice_id")); // 명확하게 invoice_id 사용
        String channelStr = body.get("delivery_channel");
        String receiverInfo = body.get("receiver_info");
        int retryCount = Integer.parseInt(body.get("retry_count")); // 0, 1, 2...
        int attemptNo = retryCount + 1; // 시도 횟수는 1부터 시작한다고 가정
        
        String maskedInfo = maskEmail(body.get("receiver_info")); // 마스킹 적용
        log.info(">>> [트리거] 수신자: {}, 채널: {}, 회차: {}", maskedInfo, channelStr, attemptNo);

        try {
            // 2. 중복 방지 선점 (READY/FAILED -> PROCESSING)
            boolean isLead = statusRepository.updateStatusToProcessing(statusId, channelStr);
            if (!isLead) {
                log.info("[중복방지] 이미 처리 중인 건입니다. ID: {}", statusId);
                acknowledge(record); // 이미 처리 중이므로 ACK 하고 종료
                return;
            }

            // 3. 모킹 발송 (1초 지연 및 1% 실패 로직 시뮬레이션)
            log.info("발송 중... (Target: {})", channelStr);
            Thread.sleep(1000); 
            
            // 테스트를 위해 일단 무조건 성공(SUCCESS)으로 가정
            boolean isSuccess = true; 
            
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
           

            // 4. 결과 기록 (성공 시)
            if (isSuccess) {
                // 상태 업데이트 (SENT)
                statusRepository.updateResult(statusId, DeliveryStatusType.SENT, now);
                
                DeliveryHistory history = DeliveryHistory.builder()
                		.deliveryHistoryId(generateTempId())
                        .invoiceId(invoiceId)
                        .attemptNo(attemptNo)
                        .deliveryChannel(DeliveryChannelType.from(channelStr))
                        .receiverInfo("masking@info.com") 
                        .status(DeliveryResultType.SUCCESS)
                        .requestedAt(now) // 실제로는 적재 시점의 시간을 쓰는 것이 좋음
                        .sentAt(now)
                        .build();

                try {
                    historyRepository.save(history);
                    log.info(">>> [성공] 이력 저장 완료. InvoiceID: {}", invoiceId);
                } catch (DuplicateKeyException e) {
                    log.warn("[중복] 이미 존재하는 이력입니다. UK 위반.");
                }
                
                
            }

            // 5. 완료 신호 (ACK)
            acknowledge(record);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("발송 중단 에러: {}", e.getMessage());
        } catch (Exception e) {
            log.error("발송 처리 중 치명적 에러: {}", e.getMessage());
        }
    }
    
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        
        String[] parts = email.split("@");
        String id = parts[0];
        String domain = parts[1];

        if (id.length() <= 2) {
            return id + "***@" + domain;
        }
        // 앞 2글자만 남기고 나머지는 별표 처리
        return id.substring(0, 2) + "***@" + domain;
    }
    
    // 테스트용 임시 ID 생성기 (단순 카운터나 시간 기반)
    private static long idCounter = 1;
    private synchronized long generateTempId() {
        return idCounter++;
    }
    
    private void acknowledge(MapRecord<String, String, String> record) {
        redisTemplate.opsForStream().acknowledge("billing:delivery:waiting", "delivery-group", record.getId());
    }
}
