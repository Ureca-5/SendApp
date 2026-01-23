package com.mycom.myapp.sendapp.delivery.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProcessResult {
    private final Long invoiceId;
    private final String channel;
    private final int attemptNo;	  // payload.retry_count + 1
    private final String status;      // SENT, FAILED
    private final LocalDateTime requestedAt;
    private final String receiverInfo; // History 기록용 
    private final boolean skipped;     // 멱등 가드에 의해 스킵 여부

    // 스킵된 경우를 위한 정적 팩토리 메서드
    public static ProcessResult skipped(Long invoiceId, String channel, int attemptNo, LocalDateTime requestedAt) {
        return ProcessResult.builder()
                .invoiceId(invoiceId)
                .channel(channel)
                .attemptNo(attemptNo)
                .status("SENT") // 이미 보냈으므로 SENT로 취급
                .requestedAt(requestedAt)
                .skipped(true)
                .build();
    }
}
