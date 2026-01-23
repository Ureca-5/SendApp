package com.mycom.myapp.sendapp.invoice.dto;
import java.sql.Date;

public record InvoiceEmailDetailRawDTO(
        Long detailId,
        Integer invoiceCategoryId, // 1,2,3,4
        String serviceName,
        Long originAmount,
        Long discountAmount,
        Long totalAmount,
        Date usageStartDate,
        Date usageEndDate
) {}
