package com.mycom.myapp.sendapp.invoice.dto;
import java.sql.Date;
import java.sql.Timestamp;

public record InvoiceEmailHeaderRawDTO(
        Long invoiceId,
        Long usersId,
        Integer billingYyyymm,
        Long totalAmount,
        Date dueDate,
        Timestamp createdAt,

        String userName,
        String userPhone,
        String paymentMethod,
        String paymentInfo,

        Date usageStartDate,
        Date usageEndDate,

        Long sumAAmount,
        Long sumBAmount,
        Long sumCAmount
) {}
