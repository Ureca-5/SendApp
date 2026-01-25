package com.mycom.myapp.sendapp.delivery.sender;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryPayload;
import com.mycom.myapp.sendapp.delivery.dto.SendResponse;
import com.mycom.myapp.sendapp.delivery.processor.TemplateRenderer;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SMSSender implements DeliverySender {
	
	private final TemplateRenderer templateRenderer;
	private final ContactProtector protector;
	private final AtomicInteger saved = new AtomicInteger(0);
	
	@Override
    public boolean supports(String channel) {
        return "SMS".equalsIgnoreCase(channel);
    }
	
	@Override
    public SendResponse send(DeliveryPayload payload) {
        
        String smsText = String.format("[LGU+ 알림] %s님,\n%s 청구금액 %s원 납부예정\n상세: 자세한 내용은 앱을 확인하세요.",
        		protector.maskedName(payload.getRecipientName()), 
                payload.getBillingYyyymm(), 
                payload.getTotalAmount());

        if (saved.get() < 10 && saved.getAndIncrement() < 10) {
            templateRenderer.saveSMS(
                payload.getInvoiceId(),
                protector.maskedName(payload.getRecipientName()),
                smsText
            );
        }

        
        return SendResponse.success();
    }
}
