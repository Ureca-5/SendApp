package com.mycom.myapp.sendapp.admin.invoice.service;

//import com.mycom.myapp.sendapp.admin.invoice.dao.BatchAttemptDao;
import com.mycom.myapp.sendapp.admin.invoice.dao.BatchFailDao;
import com.mycom.myapp.sendapp.admin.invoice.dao.BillDao;
import com.mycom.myapp.sendapp.admin.invoice.dao.InvoiceDetailDao;
import com.mycom.myapp.sendapp.admin.invoice.dto.*;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class BillService {

    private final BillDao billDao;
    private final InvoiceDetailDao invoiceDetailDao;
    //private final BatchAttemptDao attemptDao;
    private final BatchFailDao failDao;
    private final ContactProtector protector;

    public BillService(
            BillDao billDao,
            InvoiceDetailDao invoiceDetailDao,
            //BatchAttemptDao attemptDao,
            BatchFailDao failDao,
            ContactProtector protector
    ) {
        this.billDao = billDao;
        this.invoiceDetailDao = invoiceDetailDao;
        //this.attemptDao = attemptDao;
        this.failDao = failDao;
        this.protector = protector;
    }

    // =========================
    // 1) Invoices (monthly_invoice)
    // =========================

    public long countInvoices(Integer billingYyyymm, String keyword, Long invoiceId) {
        return billDao.count(billingYyyymm, keyword, invoiceId);
    }

    public List<BillRowViewDTO> listInvoices(Integer billingYyyymm, String keyword, Long invoiceId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(page, 0);
        int offset = safePage * safeSize;

        return billDao.list(billingYyyymm, keyword, invoiceId, safeSize, offset)
                .stream()
                .map(this::toBillView)
                .toList();
    }

    public BillRowRawDTO findInvoice(long invoiceId) {
        return billDao.getBill(invoiceId);
    }

    public BillRowViewDTO findInvoiceView(long invoiceId) {
        BillRowRawDTO r = findInvoice(invoiceId);
        return r == null ? null : toBillView(r);
    }

    public List<InvoiceDetailRowViewDTO> invoiceDetails(long invoiceId) {
        return invoiceDetailDao.listByInvoice(invoiceId)
                .stream()
                .map(this::toDetailView)
                .toList();
    }

    private BillRowViewDTO toBillView(BillRowRawDTO r) {
        String nameMasked = protector.maskedName(r.userName());

        return new BillRowViewDTO(
                r.invoiceId(),
                r.billingYyyymm(),
                r.usersId(),
                nameMasked,
                r.totalAmount(),
                r.totalDiscountAmount(),
                r.dueDate(),
                r.createdAt()
        );
    }

    private InvoiceDetailRowViewDTO toDetailView(InvoiceDetailRowRawDTO r) {
        int cat = r.invoiceCategoryId(); // primitive면 그대로, wrapper면 null 처리
        String usageText;

        // 메일 정책 그대로: cat==4는 단건(결제표기)
        if (cat == 4) {
            usageText = formatMetaC(r.usageStartDate(), r.usageEndDate()); // 아래 helper 추가
        } else {
            usageText = (r.usageStartDate() != null && r.usageEndDate() != null)
                    ? (r.usageStartDate() + " ~ " + r.usageEndDate())
                    : "-";
        }

        return new InvoiceDetailRowViewDTO(
                r.detailId(),
                r.invoiceCategoryId(),
                nvl(r.invoiceCategoryName(), "-"),
                r.serviceName(),
                fmtMoney(r.originAmount()),
                fmtMoney(r.discountAmount()),
                fmtMoney(r.totalAmount()),
                usageText
        );
    }

    // BillService 안에 메일과 동일한 helper만 복사
    private static String formatMetaC(LocalDate localDate, LocalDate localDate2) {
        if (localDate != null && localDate2 != null) {
            LocalDate s = localDate;
            LocalDate e = localDate2;
            if (s.equals(e)) return s +"";   // Bills는 yyyy-MM-dd로 충분
            return s + " ~ " + e;
        }
        if (localDate2 == null) return localDate+"";
        return "-";
    }


    // =========================
    // 2) Attempts (monthly_invoice_batch_attempt)
    // =========================


 

    // =========================
    // 3) Failures (monthly_invoice_batch_fail)
    // =========================

    public int countFailures(Integer targetYyyymm, Long attemptId, String errorCode) {
        return failDao.count(targetYyyymm, attemptId, errorCode);
    }

    public List<BatchFailViewDTO> listFailures(Integer targetYyyymm, Long attemptId, String errorCode, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(page, 0);
        int offset = safePage * safeSize;

        return failDao.list(targetYyyymm, attemptId, errorCode, safeSize, offset)
                .stream()
                .map(this::toFailView)
                .toList();
    }

    private BatchFailViewDTO toFailView(BatchFailRowDTO r) {
        String msg = nvl(r.errorMessage(), "");
        String shortMsg = msg;
        if (shortMsg.length() > 80) {
            shortMsg = shortMsg.substring(0, 80) + "…";
        }

        return new BatchFailViewDTO(
                r.failId(),
                r.attemptId(),
                nvl(r.invoiceCategoryName(), "-"),
                r.billingHistoryId(),
                nvl(r.errorCode(), "-"),
                msg,
                shortMsg,
                r.createdAt()
        );
    }

    // =========================
    // Helpers
    // =========================

    private static String fmtMoney(long amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.KOREA);
        return nf.format(amount);
    }

    private static String fmtDurationMs(Long durationMs) {
        if (durationMs == null) return "-";
        if (durationMs < 1000) return durationMs + "ms";

        long sec = durationMs / 1000;
        long ms = durationMs % 1000;
        long min = sec / 60;
        long s = sec % 60;

        if (min <= 0) {
            return s + "s " + ms + "ms";
        }
        return min + "m " + s + "s";
    }

    private static String nvl(String v, String dflt) {
        return (v == null || v.isBlank()) ? dflt : v;
    }
}
