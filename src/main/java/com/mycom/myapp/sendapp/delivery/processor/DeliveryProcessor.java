package com.mycom.myapp.sendapp.delivery.processor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryPayload;
import com.mycom.myapp.sendapp.delivery.dto.ProcessResult;
import com.mycom.myapp.sendapp.delivery.dto.SendResponse;
import com.mycom.myapp.sendapp.delivery.sender.DeliverySender;
import com.mycom.myapp.sendapp.delivery.worker.util.IdempotencyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryProcessor {
    
	private final List<DeliverySender> senders;
    private final IdempotencyGuard idempotencyGuard; // Redis SETNX 가드 (별도 구현)
   
    
    public ProcessResult execute(Map<String, String> dataMap) {
    	if (dataMap == null || !dataMap.containsKey("invoice_id")) {
            log.error("유효하지 않은 페이로드 감지: {}", dataMap);
            return null; 
        }
    	
    	// DTO로 변환
    	DeliveryPayload payload = DeliveryPayload.from(dataMap);
        Long invoiceId = payload.getInvoiceId();
        String channel = payload.getChannel();
        LocalDateTime requestedAt = payload.getRequestedAt();
        
        // 중복 발송 방지
        if (idempotencyGuard.isAlreadySent(invoiceId)) {
        	log.info("이미 발송된 건 - InvoiceId: {}", invoiceId); 
            return ProcessResult.skipped(invoiceId, channel, requestedAt);
        }
        
        // 발송 채널에 따라 분기 
        try {
        	DeliverySender targetSender = senders.stream()
                    .filter(s -> s.supports(channel))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 채널: " + channel));
        	
            
        	SendResponse response = targetSender.send(payload);
            
        	// 발송 성공시 멱등성 저장
        	if ("SENT".equals(response.getStatus())) {
                idempotencyGuard.markAsSent(invoiceId);
            }
            
            String finalReceiver = "EMAIL".equals(channel) ? payload.getEncEmail() : payload.getEndphone();
            
            return ProcessResult.builder()
                    .invoiceId(invoiceId)
                    .channel(channel)
                    .attemptNo(payload.getAttemptNo())
                    .status(response.getStatus())
                    .errorMessage(response.getErrorMessage())
                    .requestedAt(requestedAt)
                    .receiverInfo(finalReceiver)
                    .skipped(false)
                    .build();

        } catch (Exception e) {
            log.error("Processor Error [Invoice: {}]: {}", invoiceId, e.getMessage());
            return ProcessResult.builder()
                    .invoiceId(invoiceId)
                    .status("FAILED")
                    .build();
        }
    }
}
