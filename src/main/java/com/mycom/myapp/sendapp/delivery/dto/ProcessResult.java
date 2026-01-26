package com.mycom.myapp.sendapp.delivery.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProcessResult {
	// 공통
    private final Long invoiceId;
    private final String channel;
    private final String status;
    private final LocalDateTime requestedAt;

    // Status Table 전용
    private final LocalDateTime dueAt; // 예약 시간
    
    // History Table 전용
    private final int attemptNo;
    private final String receiverInfo; 
    private final String errorMessage; // 1% 실패 사유 기록용
    private final boolean skipped;
    
    public static ProcessResult skipped(Long id, String ch, LocalDateTime reqAt) {
        return ProcessResult.builder()
                .invoiceId(id).channel(ch).status("SENT")
                .requestedAt(reqAt).skipped(true).build();
    }
}
