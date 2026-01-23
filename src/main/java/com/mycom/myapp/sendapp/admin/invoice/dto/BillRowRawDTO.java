package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DB row for monthly_invoice join users (BILLS 화면용 최소 컬럼만)
 */
public record BillRowRawDTO(
        long invoiceId,
        long usersId,
        int billingYyyymm,

        long totalAmount,
        long totalDiscountAmount,

        LocalDate dueDate,
        LocalDateTime createdAt,

        String userName
) {}
