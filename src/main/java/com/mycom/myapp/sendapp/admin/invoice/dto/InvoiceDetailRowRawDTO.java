package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * monthly_invoice_detail row (raw)
 */
public record InvoiceDetailRowRawDTO(
        long detailId,
        long invoiceId,
        int invoiceCategoryId,
        String invoiceCategoryName,
        long billingHistoryId,
        String serviceName,
        long originAmount,
        long discountAmount,
        long totalAmount,
        LocalDate usageStartDate,
        LocalDate usageEndDate,
        LocalDateTime createdAt,
        LocalDate expiredAt
) {
}
