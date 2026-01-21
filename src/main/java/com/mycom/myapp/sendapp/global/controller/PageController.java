package com.mycom.myapp.sendapp.global.controller;

import com.mycom.myapp.sendapp.admin.invoice.dto.BillRowViewDTO;
import com.mycom.myapp.sendapp.admin.invoice.dto.InvoiceDetailRowViewDTO;
import com.mycom.myapp.sendapp.admin.invoice.service.BillService;
import com.mycom.myapp.sendapp.admin.user.dto.UserRowViewDTO;
import com.mycom.myapp.sendapp.admin.user.service.UserService;
import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingKpiDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusSummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingChannelStatusSummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingRecentHistoryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingHistoryRowDTO;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowDTO;
import com.mycom.myapp.sendapp.admin.batchjobs.service.BatchJobsService;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class PageController {

    private final UserService userService;
    private final BillService billService;
    private final SendingService sendingService;
    private final BatchJobsService batchJobsService;

    public PageController(
            UserService userService,
            BillService billService,
            SendingService sendingService,
            BatchJobsService batchJobsService
    ) {
        this.userService = userService;
        this.billService = billService;
        this.sendingService = sendingService;
        this.batchJobsService = batchJobsService;
    }

    @GetMapping("/")
public String dashboard(
        @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
        Model model
) {
    model.addAttribute("pageTitle", "Dashboard");
    model.addAttribute("activeMenu", "dashboard");
    model.addAttribute("billing_yyyymm", billingYyyymm);
    model.addAttribute("filterAction", "/");

    // --- KPI: Billing / Batch
    long billingTargetCount = billService.count(billingYyyymm, null, null);
    model.addAttribute("billingTargetCount", billingTargetCount);

    // ✅ Batch 현황 (최근 5건)
    List<BatchAttemptRowDTO> recent = batchJobsService.listAttempts(billingYyyymm, 0, 5);
    BatchAttemptRowDTO latest = (recent == null || recent.isEmpty()) ? null : recent.get(0);

    model.addAttribute("batchRecentAttempts", recent);
    model.addAttribute("batchLatestAttempt", latest);
    model.addAttribute("batchOkCount", latest == null ? 0 : latest.successCount());
    model.addAttribute("batchFailCount", latest == null ? 0 : latest.failCount());

    // --- KPI: Sending
    SendingKpiDTO kpi = sendingService.kpi(billingYyyymm);
    model.addAttribute("sendingTargetCount", kpi == null ? 0 : kpi.targetCount());
    model.addAttribute("sendingReadyCount", kpi == null ? 0 : kpi.readyCount());
    model.addAttribute("sendingSentCount", kpi == null ? 0 : kpi.sentCount());
    model.addAttribute("sendingFailedCount", kpi == null ? 0 : kpi.failedCount());

    // 최근 발송 이력(최신 8건)
    List<SendingRecentHistoryRowDTO> recentSending = sendingService.recentHistory(billingYyyymm, 8);
    model.addAttribute("recentSendingHistory", recentSending);

    return "dashboard";
}



    @GetMapping("/users")
    public String users(
            Model model,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "withdrawn", required = false) Boolean withdrawn,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "billing_yyyymm", required = false) Integer billing_yyyymm,
            @RequestParam(value = "searched", required = false, defaultValue = "0") Integer searched
    ) {
        int p = (page == null) ? 0 : Math.max(page, 0);
        boolean isSearched = (searched != null && searched == 1);

        List<UserRowViewDTO> users = userService.list(isSearched, keyword, email, phone, withdrawn, p);

        int total = isSearched ? userService.countIfNeeded(keyword, email, phone, withdrawn) : 0;

        model.addAttribute("users", users);
        model.addAttribute("keyword", keyword);
        model.addAttribute("email", email);
        model.addAttribute("phone", phone);
        model.addAttribute("withdrawn", withdrawn);
        model.addAttribute("page", p);
        model.addAttribute("size", UserService.FIXED_SIZE);
        model.addAttribute("billing_yyyymm", billing_yyyymm);

        model.addAttribute("searched", isSearched);
        model.addAttribute("total", total);

        return "users";
    }


    // ✅ 청구서 발행 내역
    @GetMapping("/bills")
    public String bills(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "keyword", required = false) String keyword, // users_id or name
            @RequestParam(value = "users_id", required = false) Long usersId,
            @RequestParam(value = "invoice_id", required = false) Long invoiceId, // 상세 선택(옵션)
            Model model
    ) {
        model.addAttribute("pageTitle", "Bills");
        model.addAttribute("activeMenu", "bills");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/bills");

        long totalL = billService.count(billingYyyymm, keyword, invoiceId);
        int total = (totalL > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalL;

        List<BillRowViewDTO> rows = billService.list(billingYyyymm, keyword, invoiceId, page, size);

        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("bills", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);
        model.addAttribute("invoice_id", invoiceId);

        if (invoiceId != null) {
            List<InvoiceDetailRowViewDTO> detail = billService.details(invoiceId);
            model.addAttribute("selectedInvoiceId", invoiceId);
            model.addAttribute("details", detail);
        }

        return "bills";
    }

    // ✅ 수납/미납 관리 (ERD에 납부상태가 없으니 bills 재사용 수준)
    // ✅ Payments (보류): ERD에 납부상태가 반영되기 전까지는 Bills로 흡수
@GetMapping("/payments")
public String payments(
        @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
        Model model
) {
    model.addAttribute("pageTitle", "Payments");
    model.addAttribute("activeMenu", "payments");
    model.addAttribute("billing_yyyymm", billingYyyymm);
    model.addAttribute("filterAction", "/payments");
    return "payments";
}


    // ✅ 발송 이력/재발송(일단 조회만)
    @GetMapping("/sending")
    public String sending(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "users_id", required = false) Long usersId,
            @RequestParam(value = "invoice_id", required = false) Long invoiceId,
            Model model
    ) {
        model.addAttribute("pageTitle", "Sending");
        model.addAttribute("activeMenu", "sending");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/sending");

        int total = sendingService.count(billingYyyymm, status, channel, usersId, invoiceId);
        var rows = sendingService.list(billingYyyymm, status, channel, usersId, invoiceId, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("sendingRows", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("status", status);
        model.addAttribute("channel", channel);
        model.addAttribute("users_id", usersId);
        model.addAttribute("invoice_id", invoiceId);

        // 월별 통계 (Sending Home)
        model.addAttribute("statusSummaries", sendingService.statusSummary(billingYyyymm));
        model.addAttribute("channelStatusSummaries", sendingService.channelStatusSummary(billingYyyymm));

        if (invoiceId != null) {
            model.addAttribute("selectedInvoiceId", invoiceId);
            model.addAttribute("historyRows", sendingService.history(invoiceId));
        }

        return "sending";
    }

    
// ✅ 유저 단건 발송 조회 (YYYYMM + users_id)
@GetMapping("/sending/user")
public String sendingUser(
        @RequestParam(value = "billing_yyyymm") Integer billingYyyymm,
        @RequestParam(value = "users_id") Long usersId,
        @RequestParam(value = "invoice_id", required = false) Long invoiceId,
        Model model
) {
    model.addAttribute("pageTitle", "Sending User");
    model.addAttribute("activeMenu", "sending");
    model.addAttribute("billing_yyyymm", billingYyyymm);
    model.addAttribute("users_id", usersId);

    var userRows = sendingService.listByUser(billingYyyymm, usersId);
    model.addAttribute("userRows", userRows);

    if (invoiceId != null) {
        model.addAttribute("selectedInvoiceId", invoiceId);
        List<SendingHistoryRowDTO> history = sendingService.history(invoiceId);
        model.addAttribute("historyRows", history);
    }

    return "sending-user";
}

// ✅ 단건 재발송 화면
@GetMapping("/sending/resend-user")
public String resendUserPage(
        @RequestParam(value = "billing_yyyymm") Integer billingYyyymm,
        @RequestParam(value = "users_id") Long usersId,
        Model model
) {
    model.addAttribute("pageTitle", "Resend User");
    model.addAttribute("activeMenu", "sending");
    model.addAttribute("billing_yyyymm", billingYyyymm);
    model.addAttribute("users_id", usersId);
    return "sending-resend-user";
}

// ✅ 일괄 재발송 화면
@GetMapping("/sending/resend-bulk")
public String resendBulkPage(
        @RequestParam(value = "billing_yyyymm") Integer billingYyyymm,
        Model model
) {
    model.addAttribute("pageTitle", "Resend Bulk");
    model.addAttribute("activeMenu", "sending");
    model.addAttribute("billing_yyyymm", billingYyyymm);
    return "sending-resend-bulk";
}

// ✅ 청구서 템플릿 프리뷰 (Email)
@GetMapping("/bills/preview/email")
public String billEmailPreview(
        @RequestParam("invoice_id") Long invoiceId,
        Model model
) {
    BillRowViewDTO bill = billService.getBill(invoiceId);
    List<InvoiceDetailRowViewDTO> details = billService.details(invoiceId);

    model.addAttribute("bill", bill);
    model.addAttribute("details", details);
    return "email-preview";
}

// ✅ 청구서 템플릿 프리뷰 (SMS)
@GetMapping("/bills/preview/sms")
public String billSmsPreview(
        @RequestParam("invoice_id") Long invoiceId,
        Model model
) {
    BillRowViewDTO bill = billService.getBill(invoiceId);

    String smsText = String.format(
            "[LGU+] %s 청구서\n합계 %s / 납부기한 %s\n상세는 이메일 또는 앱에서 확인하세요.",
            bill == null ? "-" : bill.billingMonthText(),
            bill == null ? "-" : bill.totalAmountText(),
            bill == null ? "-" : bill.dueDateText()
    );

    model.addAttribute("bill", bill);
    model.addAttribute("smsText", smsText);
    return "sms-preview";
}

// ✅ 배치 작업 로그
    @GetMapping("/batch-jobs")
    public String batchJobs(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            Model model
    ) {
        model.addAttribute("pageTitle", "Batch Jobs");
        model.addAttribute("activeMenu", "batch-jobs");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/batch-jobs");

        int total = batchJobsService.countAttempts(billingYyyymm);
        var rows = batchJobsService.listAttempts(billingYyyymm, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("attemptRows", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);

        return "batch-jobs";
    }

    // ---- 호환 redirect (기존 라우트)
    @GetMapping("/invoices")
    public String invoicesRedirect(
            @RequestParam(value = "users_id", required = false) Long usersId,
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm
    ) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/bills");
        if (billingYyyymm != null) b.queryParam("billing_yyyymm", billingYyyymm);
        if (usersId != null) b.queryParam("keyword", usersId);
        return "redirect:" + b.toUriString();
    }

    @GetMapping("/delivery")
    public String deliveryRedirect() { return "redirect:/sending"; }

    @GetMapping("/batch")
    public String batchRedirect() { return "redirect:/batch-jobs"; }
}
