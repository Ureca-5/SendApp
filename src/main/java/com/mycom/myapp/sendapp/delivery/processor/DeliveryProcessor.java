package com.mycom.myapp.sendapp.delivery.processor;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.mycom.myapp.sendapp.delivery.dto.ProcessResult;
import com.mycom.myapp.sendapp.delivery.service.DeliveryBatchWorker;
import com.mycom.myapp.sendapp.delivery.worker.util.IdempotencyGuard;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryProcessor {
    private final ContactProtector protector;
    private final TemplateRenderer templateRenderer; // Thymeleaf 렌더러 (별도 구현)
    private final IdempotencyGuard idempotencyGuard; // Redis SETNX 가드 (별도 구현)
    
    private final AtomicInteger fileSaveCounter = new AtomicInteger(0);
    
    public ProcessResult execute(Map<String, String> payload) {
//    	log.info("[Raw Payload Check] : {}", payload);
    	
    	if (payload == null || !payload.containsKey("invoice_id")) {
            log.error("유효하지 않은 페이로드 감지: {}", payload);
            return null; 
        }
    	
    	Long invoiceId = Long.valueOf(payload.get("invoice_id"));
        String channel = payload.get("delivery_channel");
        int currentAttemptNo = Integer.parseInt(payload.getOrDefault("retry_count", "0")) + 1;
        
        
        if (idempotencyGuard.isAlreadySent(invoiceId)) {
            return ProcessResult.skipped(invoiceId, channel, currentAttemptNo);
        }

        try {
        	String rawName = payload.get("recipient_name");
            String maskedName = protector.maskedName(rawName); 

            String email = payload.get("email");
            String phone = payload.get("phone");
            
            String maskedEmail = protector.maskedEmail(EncryptedString.of(email));
            String maskedPhone = protector.maskedPhone(EncryptedString.of(phone));
            
            // 템플릿 사용
            String htmlContent = templateRenderer.render(payload, maskedName, maskedEmail, maskedPhone);
//            String htmlContent = templateRenderer.render(payload, maskedName, payload.get("receiver_info"), payload.get("receiver_info"));
            
            // 테스트를 위해 100건만 파일 저장
            String fileName = "NOT_SAVED";
            if (fileSaveCounter.getAndIncrement() < 100) {
            	fileName = templateRenderer.saveToFile(invoiceId, maskedName, htmlContent);
            }
            
            // 1초 지연 및 1% 실패 확률
//            Thread.sleep(1000);
            boolean isSuccess = ThreadLocalRandom.current().nextInt(100) != 0;

            if (isSuccess) {
                idempotencyGuard.markAsSent(invoiceId); // Redis 완료 마킹
            }
            
            String finalReceiver = "EMAIL".equals(channel) ? email : phone;
            
            return ProcessResult.builder()
                    .invoiceId(invoiceId)
                    .channel(channel)
                    .attemptNo(currentAttemptNo)
                    .status(isSuccess ? "SENT" : "FAILED")
                    .receiverInfo(finalReceiver)
                    .skipped(false)
                    .build();

        } catch (Exception e) {
            return ProcessResult.builder()
                    .invoiceId(invoiceId).channel(channel).attemptNo(currentAttemptNo)
                    .status("FAILED").skipped(false).build();
        }
    }
}