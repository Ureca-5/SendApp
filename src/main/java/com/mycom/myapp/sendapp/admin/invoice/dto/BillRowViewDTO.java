package com.mycom.myapp.sendapp.admin.invoice.dto;

public record BillRowViewDTO(
    long invoiceId,
    long usersId,
    String billingMonthText,

    String userNameMasked,
    String userPhoneMasked,

    String planAmountText,
    String addonAmountText,
    String etcAmountText,
    String discountAmountText,
    String totalAmountText,

    String dueDateText,
    String createdAtText
) {}
