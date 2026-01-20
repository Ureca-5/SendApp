package com.mycom.myapp.sendapp.admin.invoice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BillRowRawDTO(
    long invoiceId,
    long usersId,
    int billingYyyymm,

    long planAmount,
    long addonAmount,
    long etcAmount,
    long discountAmount,
    long totalAmount,

    LocalDate dueDate,
    LocalDateTime createdAt,

    String userName,
    String phoneEnc
) {}
