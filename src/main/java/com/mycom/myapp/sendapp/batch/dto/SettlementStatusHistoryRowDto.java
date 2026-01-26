package com.mycom.myapp.sendapp.batch.dto;

import com.mycom.myapp.sendapp.batch.enums.SettlementStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 정산 상태 이력 dto
 */
@Getter
@Builder
public class SettlementStatusHistoryRowDto {
    private Long settlementStatusHistoryId; // insert 시 null
    private Long invoiceId;
    private Long attemptId;
    private SettlementStatus fromStatus;
    private SettlementStatus toStatus;
    private LocalDateTime changedAt;
    private String reasonCode; // null 가능
}
