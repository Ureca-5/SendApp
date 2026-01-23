package com.mycom.myapp.sendapp.invoice.dto;

import java.util.List;

public record InvoiceEmailVM(
        String billMonthText,
        String customerNameMasked,
        String phoneMasked,
        String totalAmountText,
        String usageStartText,
        String usageEndText,
        String billCreatedDateText,
        String paymentMethodText,
        String paymentInfoText,
        String sumAAmountText,
        String sumBAmountText,
        String sumCAmountText,
        List<InvoiceEmailLineVM> aDetails,
        List<InvoiceEmailLineVM> bDetails,
        List<InvoiceEmailLineVM> cDetails
) {}
