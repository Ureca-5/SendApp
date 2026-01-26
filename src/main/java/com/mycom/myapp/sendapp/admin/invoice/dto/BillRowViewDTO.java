package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * monthly_invoice list row for UI (bills.html 계약 기준)
 */
public record BillRowViewDTO(
        long invoiceId,
        int billingYyyymm,
        long usersId,
        String userName,
        long totalAmount,
        long totalDiscountAmount,
        LocalDate dueDate,
        LocalDateTime createdAt
) {
}
