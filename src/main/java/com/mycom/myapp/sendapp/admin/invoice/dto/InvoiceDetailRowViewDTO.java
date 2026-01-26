package com.mycom.myapp.sendapp.admin.invoice.dto;

/**
 * monthly_invoice_detail view row for UI.
 *
 * Keep it string-based so Thymeleaf template stays simple.
 */
public record InvoiceDetailRowViewDTO(
        long detailId,
        int invoiceCategoryId,
        String categoryName,
        String serviceName,
        String originAmount,
        String discountAmount,
        String totalAmount,
        String usageRange
) {
}
