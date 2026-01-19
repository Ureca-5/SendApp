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
        Map<String, String> body = record.getValue();
        
        // Redis JSON 데이터에서 필요한 정보 추출
        Long statusId = Long.valueOf(body.get("delivery_status_id"));
        Long invoiceId = Long.valueOf(body.get("invoice_id")); 
        String channelStr = body.get("delivery_channel");
//        String receiverInfo = body.get("receiver_info");
        int retryCount = Integer.parseInt(body.get("retry_count")); 
        int attemptNo = retryCount; 
        
        String maskedInfo = maskEmail(body.get("receiver_info")); 
        log.info(">>> [트리거] 수신자: {}, 채널: {}, 회차: {}", maskedInfo, channelStr, attemptNo);

        try {
           
            boolean isLead = statusRepository.updateStatusToProcessing(statusId, channelStr);
            if (!isLead) {
                log.info("[중복방지] 이미 처리 중인 건입니다. ID: {}", statusId);
                acknowledge(record);
                return;
            }

            
            log.info("발송 중... (Target: {})", channelStr);
            Thread.sleep(1000); 
            
            
            boolean isSuccess = true; 
            
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
           

            
            if (isSuccess) {
                
                statusRepository.updateResult(statusId, DeliveryStatusType.SENT, now);
                
                DeliveryHistory history = DeliveryHistory.builder()
                		.deliveryHistoryId(generateTempId())
                        .invoiceId(invoiceId)
                        .attemptNo(attemptNo)
                        .deliveryChannel(DeliveryChannelType.from(channelStr))
                        .receiverInfo("masking@info.com") 
                        .status(DeliveryResultType.SUCCESS)
                        .requestedAt(now) 
                        .sentAt(now)
                        .build();

                try {
                    historyRepository.save(history);
                    log.info(">>> [성공] 이력 저장 완료. InvoiceID: {}", invoiceId);
                } catch (DuplicateKeyException e) {
                    log.warn("[중복] 이미 존재하는 이력입니다. UK 위반.");
                }
                
                
            }

            
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
        
        return id.substring(0, 2) + "***@" + domain;
    }
    
    
    private static long idCounter = 1;
    private synchronized long generateTempId() {
        return idCounter++;
    }
    
    private void acknowledge(MapRecord<String, String, String> record) {
        redisTemplate.opsForStream().acknowledge("billing:delivery:waiting", "delivery-group", record.getId());
    }
}