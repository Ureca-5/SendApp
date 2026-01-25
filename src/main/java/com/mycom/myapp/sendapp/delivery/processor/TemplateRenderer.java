package com.mycom.myapp.sendapp.delivery.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryPayload;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private final TemplateEngine templateEngine; // Thymeleaf 엔진
    private final ContactProtector protector;

    public String render(DeliveryPayload payload) {
 	
        Context context = new Context();
        // 상세 조회용 링크
        String detailLink = String.format("http://localhost:8080/delivery/detail/" + payload.getInvoiceId());        
        
        String encEmail = payload.getEncEmail();   // 암호문
        String encPhone = payload.getEndphone();   // 암호문
        String maskedEmail;
        String maskedPhone;
        String maskedName = protector.maskedName(payload.getRecipientName());
        
        try {
            maskedEmail = protector.maskedEmail(EncryptedString.of(encEmail));
            maskedPhone = protector.maskedPhone(EncryptedString.of(encPhone));
        } catch (Exception e) {
            log.error("PII 보호 처리 실패 - invoiceId={}, err={}", payload.getInvoiceId(), e.toString());
            maskedEmail = "(이메일 확인 불가)";
            maskedPhone = "(번호 확인 불가)";
        }
        
        context.setVariable("resultDto", payload);
        context.setVariable("maskedEmail", maskedEmail);
        context.setVariable("maskedPhone", maskedPhone);
        context.setVariable("maskedName", maskedName);

        context.setVariable("detailLink", detailLink);
        
        return templateEngine.process("bill_template", context);
    }

    // 생성된 이메일 HTML을 파일로 저장
    public String saveToFile(Long invoiceId, String maskedName, String htmlContent) {
        try {
            // 규칙: invoiceId_마스킹이름_UUID.html
            String fileName = String.format("%s_%s_%s.html", 
                invoiceId, maskedName, UUID.randomUUID());
            
            String directoryPath = "logs/email_invoices/";
            Files.createDirectories(Paths.get(directoryPath));
            
            String filePath = directoryPath + fileName;
            Files.writeString(Paths.get(filePath), htmlContent);
            
            return fileName;
        } catch (Exception e) {
            log.error("파일 저장 실패 (InvoiceID: {}): {}", invoiceId, e.getMessage());
            return "FILE_SAVE_ERROR";
        }
    }
    
    // 생성된 SMS TXT를 파일로 저장
    public void saveSMS(Long invoiceId, String maskedName, String content) {
        try {
        	
        	String directoryPath = "logs/sms_invoices/";
        	Files.createDirectories(Paths.get(directoryPath));
        	
        	String fileName = String.format("%s_%s_%s.html", 
                    invoiceId, maskedName, UUID.randomUUID());
        	
        	String logLine = String.format("[%s] ID: %d | %s", 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                    invoiceId, content);
            
        	Files.writeString(Paths.get(directoryPath + fileName), logLine);
        	
        } catch (Exception e) {
            log.error("SMS 파일 저장 실패: {}", e.getMessage());
        }
    }
}