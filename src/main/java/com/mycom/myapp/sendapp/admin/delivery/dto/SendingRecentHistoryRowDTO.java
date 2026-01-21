package com.mycom.myapp.sendapp.admin.delivery.dto;

import java.time.LocalDateTime;

public record SendingRecentHistoryRowDTO(
        long invoiceId,
        long usersId,
        String channel,
        String status,
        String errorMessage,
        LocalDateTime requestedAt
) {
    public String requestedAtText() {
        return requestedAt == null ? "-" : requestedAt.toString().replace('T',' ');
    }
}
