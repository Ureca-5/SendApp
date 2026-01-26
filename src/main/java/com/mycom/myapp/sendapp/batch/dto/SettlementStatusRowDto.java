package com.mycom.myapp.sendapp.batch.dto;

import com.mycom.myapp.sendapp.batch.enums.SettlementStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * settlement_status 매핑/배치 반영 DTO
 * - 모든 필드는 wrapper 타입으로 통일
 */
@Getter
@Builder
public class SettlementStatusRowDto {
    private Long invoiceId;          // PK & FK (monthly_invoice.invoice_id)
    private SettlementStatus status; // NONE/READY/PROCESSING/COMPLETED/FAILED
    private LocalDateTime lastAttemptAt;
    private LocalDateTime createdAt;
}
