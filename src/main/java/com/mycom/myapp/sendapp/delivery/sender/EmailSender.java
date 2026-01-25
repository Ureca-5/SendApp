package com.mycom.myapp.sendapp.delivery.sender;

import java.util.concurrent.ThreadLocalRandom;

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
public class EmailSender implements DeliverySender {
	
	private final TemplateRenderer templateRenderer;
	private final ContactProtector protector;
	
	@Override
    public boolean supports(String channel) {
        return "EMAIL".equalsIgnoreCase(channel);
    }
	
	@Override
    public SendResponse send(DeliveryPayload payload) {
		
		try {
            // HTML 템플릿 렌더링
			String htmlContent = templateRenderer.render(payload);

            // 테스트용 파일 저장 
			if (payload.getInvoiceId() % 1000 == 0) {
                templateRenderer.saveToFile(
                    payload.getInvoiceId(), 
                    protector.maskedName(payload.getRecipientName()), 
                    htmlContent
                );
            }
			
            // 1% 실패 확률 
            if (ThreadLocalRandom.current().nextInt(100) == 0) {
            	return SendResponse.fail("MOCK_API_SERVER_ERROR");
            }

            return SendResponse.success();
        } catch (Exception e) {
            log.error("Email Render Error: {}", e.getMessage());
            return SendResponse.fail("RENDER_ERROR");
        }
   
		
	}
}
