//package com.mycom.myapp.sendapp.delivery.service;
//
//import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.*;
//
//import com.mycom.myapp.sendapp.delivery.entity.DeliveryHistory;
//import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
//import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryResultType;
//import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;
//import com.mycom.myapp.sendapp.delivery.repository.DeliveryHistoryRepository;
//import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//import org.springframework.data.redis.connection.stream.MapRecord;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.stream.StreamListener;
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class DeliveryWorker implements StreamListener<String, MapRecord<String, String, String>> {
//
//    private final DeliveryStatusRepository statusRepository;
//    private final DeliveryHistoryRepository historyRepository;
//    private final StringRedisTemplate redisTemplate;
//
//    @Override
//    public void onMessage(MapRecord<String, String, String> record) {
//        Map<String, String> body = record.getValue();
//        
//        // 발송 상태와 이력 기록시 필요한 정보
//        Long invoiceId = Long.valueOf(body.get("invoice_id")); 
//        String channelStr = body.get("delivery_channel");
//        int attemptNo = Integer.parseInt(body.get("retry_count")); 
//        String rawReceiverInfo = body.get("receiver_info"); // 암호화된 상태
//        
////        String maskedInfo = maskEmail(rawReceiverInfo);
//        
//        log.info(">>> [발송정보] 청구서ID: {}, 채널: {}, 회차: {}", invoiceId, channelStr, attemptNo);
//
//        try {
//           
//            boolean isLead = statusRepository.updateStatusToProcessing(invoiceId, channelStr);
//            if (!isLead) {
//            	log.info("[중복방지] 이미 처리 중이거나 완료된 건입니다. 청구서ID: {}", invoiceId);
//                acknowledge(record);
//                return;
//            }
//
//            
//            log.info(">>> 발송 프로세스 시작... (InvoiceID: {})", invoiceId);
//            Thread.sleep(1000); 
//            
//            
//            // 성공 여부( 1% 랜덤 실패 로직 추가함.)
//            boolean isSuccess = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) != 0;
//            java.time.LocalDateTime now = java.time.LocalDateTime.now();
//            
//            if (isSuccess) {
//                handleSuccess(invoiceId, attemptNo, channelStr, rawReceiverInfo, now);
//            } else {
//                handleFailure(invoiceId, attemptNo, channelStr, rawReceiverInfo, now);
//            }
//            
//            acknowledge(record);
//        
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            log.error(">>> [중단] 프로세스 종료: {}", e.getMessage());
//        
//        } catch (Exception e) {
//            log.error(">>> [장애] InvoiceID {} 처리 중 에러 (ACK 보류): {}", invoiceId, e.getMessage());
//        }
//
//    }
//    
//    private void handleSuccess(Long invoiceId, int attemptNo, String channelStr, String maskedInfo, java.time.LocalDateTime now) {
//        
//    	saveHistoryWithStrictCheck(invoiceId, attemptNo, channelStr, maskedInfo, DeliveryResultType.SUCCESS, now);
//        
//        statusRepository.updateResult(invoiceId, DeliveryStatusType.SENT, now);
//        
//        log.info(">>> [결과:성공] DB 업데이트 및 이력 저장 완료. InvoiceID: {}", invoiceId);
//    }
//
//    private void handleFailure(Long invoiceId, int attemptNo, String channelStr, String maskedInfo, java.time.LocalDateTime now) {
//        
//    	saveHistoryWithStrictCheck(invoiceId, attemptNo, channelStr, maskedInfo, DeliveryResultType.FAIL, now);
//        
//        statusRepository.updateResult(invoiceId, DeliveryStatusType.FAILED, now);
//        
//        log.warn(">>> [결과:실패] 발송 실패 기록 완료. InvoiceID: {}", invoiceId);
//    }
//
//    private DeliveryHistory createHistory(Long invoiceId, int attemptNo, String channel, String info, DeliveryResultType result, java.time.LocalDateTime now) {
//        return DeliveryHistory.builder()
//                .invoiceId(invoiceId)
//                .attemptNo(attemptNo)
//                .deliveryChannel(DeliveryChannelType.from(channel))
//                .receiverInfo(info) 
//                .status(result)
//                .requestedAt(now) 
//                .sentAt(now)
//                .build();
//    }
//
//    private void saveHistoryWithStrictCheck(Long invoiceId, int attemptNo, String channel, String info, DeliveryResultType result, java.time.LocalDateTime now) {
//        DeliveryHistory history = createHistory(invoiceId, attemptNo, channel, info, result, now);
//        try {
//            historyRepository.save(history);
//        } catch (org.springframework.dao.DuplicateKeyException e) {
//            // [중복 처리] 
//            log.warn("[중복 발송] 이미 동일한 이력이 존재함 (InvoiceID: {}, 회차: {})", invoiceId, attemptNo);
//            // 이미 처리된 건이므로 프로세스를 더 진행하지 않도록 예외 던짐 (상태 업데이트 방지)
//            throw new RuntimeException("이미 처리된 중복 메시지입니다.");
//        } catch (Exception e) {
//            log.error("❌ [이력저장실패] DB 치명적 에러: {}", e.getMessage());
//            throw e; 
//        }
//    }
//
//    private void acknowledge(MapRecord<String, String, String> record) {
//        // 하드코딩 제거: 상수를 사용하여 안전하게 ACK
//        redisTemplate.opsForStream().acknowledge(WAITING_STREAM, GROUP_NAME, record.getId());
//    }
//    
////    private String maskEmail(String email) {
////        if (email == null || !email.contains("@")) return email;
////        
////        String[] parts = email.split("@");
////        String id = parts[0];
////        String domain = parts[1];
////
////        if (id.length() <= 2) {
////            return id + "***@" + domain;
////        }
////        
////        return id.substring(0, 2) + "***@" + domain;
////    }
//    
//    
//}