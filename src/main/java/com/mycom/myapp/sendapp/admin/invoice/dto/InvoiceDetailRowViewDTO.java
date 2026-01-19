package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDateTime;

public record InvoiceDetailRowViewDTO(
        long detailId,
        int invoiceCategoryId,
        String serviceName,
        long originAmount,
        long discountAmount,
        long totalAmount,
        LocalDateTime usageStartDate,
        LocalDateTime usageEndDate
) {}
