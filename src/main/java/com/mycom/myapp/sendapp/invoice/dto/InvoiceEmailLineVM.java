package com.mycom.myapp.sendapp.invoice.dto;
import java.util.List;

public record InvoiceEmailLineVM(
        String serviceNameText,
        String metaText,
        String lineAmountText,
        String discountTextOrNull
) {}

