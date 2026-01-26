package com.mycom.myapp.sendapp.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * monthly_invoice_batch_fail insert용 DTO
 * - 모든 필드는 wrapper 타입
 */
@Getter
@Builder
public class MonthlyInvoiceBatchFailRowDto {
    private Long failId;              // AUTO_INCREMENT (insert 시 null)
    private Long attemptId;           // FK
    private String errorCode;         // NOT NULL
    private String errorMessage;      // nullable(text)
    private LocalDateTime createdAt;  // NOT NULL
    private Long invoiceId;
    private Integer invoiceCategoryId;// NOT NULL (invoice_category_id)
    private Long billingHistoryId;    // NOT NULL
}
