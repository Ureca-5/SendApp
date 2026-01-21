package com.mycom.myapp.sendapp.delivery.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private final TemplateEngine templateEngine; // Thymeleaf 엔진

    /**
     * 데이터를 받아 HTML 문자열로 렌더링
     */
    public String render(Map<String, String> payload, String maskedName, String email, String phone) {
    	log.info("[Render Data Check] ID: {}, Name: {}, Email: {}", 
                payload.get("invoice_id"), maskedName, email);
    	
        Context context = new Context();
        
        java.util.Map<String, Object> templateData = new java.util.HashMap<>();
        templateData.put("invoiceId", payload.get("invoice_id"));
        templateData.put("name", maskedName);
        templateData.put("email", email);
        templateData.put("phone", phone);
        templateData.put("channel", payload.get("delivery_channel"));
        templateData.put("month", payload.get("billing_yyyymm"));

        // 템플릿의 [[${resultDto.name}]] 과 매핑됩니다.
        context.setVariable("resultDto", templateData);

        return templateEngine.process("bill_template", context);
    }

    // 생성된 HTML을 파일로 저장 (테스트용)
    public String saveToFile(Long invoiceId, String maskedName, String htmlContent) {
        try {
            // 규칙: invoiceId_마스킹이름_UUID.html
            String fileName = String.format("%s_%s_%s.html", 
                invoiceId, maskedName, UUID.randomUUID());
            
            String directoryPath = "logs/test_invoices/";
            Files.createDirectories(Paths.get(directoryPath));
            
            String filePath = directoryPath + fileName;
            Files.writeString(Paths.get(filePath), htmlContent);
            
            return fileName;
        } catch (Exception e) {
            log.error("파일 저장 실패 (InvoiceID: {}): {}", invoiceId, e.getMessage());
            return "FILE_SAVE_ERROR";
        }
    }
}