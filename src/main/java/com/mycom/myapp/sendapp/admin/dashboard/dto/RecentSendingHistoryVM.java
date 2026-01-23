// src/main/java/com/mycom/myapp/sendapp/admin/dashboard/dto/RecentSendingHistoryVM.java
package com.mycom.myapp.sendapp.admin.dashboard.dto;

public record RecentSendingHistoryVM(
        long invoiceId,
        String channel,
        String status,
        String requestedAtText,
        String errorMessage
) {}
