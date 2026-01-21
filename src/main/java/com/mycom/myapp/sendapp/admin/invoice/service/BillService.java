package com.mycom.myapp.sendapp.admin.invoice.service;

import com.mycom.myapp.sendapp.admin.invoice.dao.BillDao;
import com.mycom.myapp.sendapp.admin.invoice.dao.InvoiceDetailDao;
import com.mycom.myapp.sendapp.admin.invoice.dto.BillRowRawDTO;
import com.mycom.myapp.sendapp.admin.invoice.dto.BillRowViewDTO;
import com.mycom.myapp.sendapp.admin.invoice.dto.InvoiceDetailRowRawDTO;
import com.mycom.myapp.sendapp.admin.invoice.dto.InvoiceDetailRowViewDTO;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class BillService {

    private final BillDao billDao;
    private final InvoiceDetailDao invoiceDetailDao;
    private final ContactProtector protector;

    private static final NumberFormat KRW = NumberFormat.getNumberInstance(Locale.KOREA);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public BillService(BillDao billDao, InvoiceDetailDao invoiceDetailDao, ContactProtector protector) {
        this.billDao = billDao;
        this.invoiceDetailDao = invoiceDetailDao;
        this.protector = protector;
    }

    public long count(Integer billingYyyymm, String keyword, Long invoiceId) {
        return billDao.count(billingYyyymm, keyword, invoiceId);
    }

    public List<BillRowViewDTO> list(Integer billingYyyymm, String keyword, Long invoiceId, int page, int size) {
        int offset = page * size;
        List<BillRowRawDTO> rows = billDao.find(billingYyyymm, keyword, invoiceId, size, offset);

        return rows.stream().map(r -> {
            String monthText = formatBillingMonth(r.billingYyyymm());

            String nameMasked = protector.maskedName(r.userName());
            String phoneMasked;
            try {
                phoneMasked = protector.maskedPhone(EncryptedString.of(r.phoneEnc()));
            } catch (Exception e) {
                phoneMasked = "(decrypt-failed)";
            }

            return new BillRowViewDTO(
                r.invoiceId(),
                r.usersId(),
                monthText,
                nameMasked,
                phoneMasked,
                money(r.planAmount()),
                money(r.addonAmount()),
                money(r.etcAmount()),
                money(r.discountAmount()),
                money(r.totalAmount()),
                r.dueDate() == null ? "-" : r.dueDate().toString(),
                r.createdAt() == null ? "-" : r.createdAt().format(DT)
            );
        }).toList();
    }

    public BillRowViewDTO getBill(long invoiceId) {
    BillRowRawDTO r = billDao.findOne(invoiceId);
    if (r == null) return null;

    String monthText = formatBillingMonth(r.billingYyyymm());

    String nameMasked = protector.maskedName(r.userName());
    String phoneMasked;
    try {
        phoneMasked = protector.maskedPhone(EncryptedString.of(r.phoneEnc()));
    } catch (Exception e) {
        phoneMasked = "(decrypt-failed)";
    }

    return new BillRowViewDTO(
            r.invoiceId(),
            r.usersId(),
            monthText,
            nameMasked,
            phoneMasked,
            money(r.planAmount()),
            money(r.addonAmount()),
            money(r.etcAmount()),
            money(r.discountAmount()),
            money(r.totalAmount()),
            r.dueDate() == null ? "-" : r.dueDate().toString(),
            r.createdAt() == null ? "-" : r.createdAt().format(DT)
    );
}

// ✅ 추가: 상세 조회
    public List<InvoiceDetailRowViewDTO> details(long invoiceId) {
        List<InvoiceDetailRowRawDTO> rows = invoiceDetailDao.findByInvoiceId(invoiceId);
        return rows.stream()
                .map(r -> new InvoiceDetailRowViewDTO(
                        r.detailId(),
                        r.invoiceCategoryId(),
                        r.serviceName(),
                        r.originAmount(),
                        r.discountAmount(),
                        r.totalAmount(),
                        r.usageStartDate(),
                        r.usageEndDate()
                ))
                .toList();
    }

    private static String money(long v) {
        return KRW.format(v) + "원";
    }

    private static String formatBillingMonth(int yyyymm) {
        int yyyy = yyyymm / 100;
        int mm = yyyymm % 100;
        return yyyy + "년 " + mm + "월";
    }
}
