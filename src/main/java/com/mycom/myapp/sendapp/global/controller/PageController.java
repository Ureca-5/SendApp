package com.mycom.myapp.sendapp.global.controller;

import com.mycom.myapp.sendapp.admin.batchjobs.service.BatchJobsService;
import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
import com.mycom.myapp.sendapp.admin.invoice.service.BillService;
import com.mycom.myapp.sendapp.admin.user.service.UserService;

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

        // ⚠️ 네 BatchJobsService에 listAttempts(Integer,int,int)가 없다고 뜸.
        // 그래서 여기서 배치 최근 시도 조회는 일단 제거 (서비스 시그니처 확인 후 다시 연결).
        model.addAttribute("batchRecentAttempts", List.of());
        model.addAttribute("batchLatestAttempt", null);

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

        var users = userService.list(isSearched, keyword, email, phone, withdrawn, p);
        int total = isSearched ? userService.countIfNeeded(keyword, email, phone, withdrawn) : 0;

        model.addAttribute("pageTitle", "Users");
        model.addAttribute("activeMenu", "users");
        model.addAttribute("filterAction", "/users");

        model.addAttribute("users", users);
        model.addAttribute("total", total);
        model.addAttribute("searched", isSearched);
        model.addAttribute("page", p);
        model.addAttribute("size", com.mycom.myapp.sendapp.admin.user.service.UserService.FIXED_SIZE);

        model.addAttribute("billing_yyyymm", billing_yyyymm);
        model.addAttribute("keyword", keyword);
        model.addAttribute("email", email);
        model.addAttribute("phone", phone);
        model.addAttribute("withdrawn", withdrawn);

        return "users";
    }

    @GetMapping("/bills")
    public String bills(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "invoice_id", required = false) Long invoiceId,
            Model model
    ) {
        model.addAttribute("pageTitle", "Bills");
        model.addAttribute("activeMenu", "bills");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/bills");
        model.addAttribute("keyword", keyword);
        model.addAttribute("page", page);
        model.addAttribute("size", size);

        // ✅ BillService 시그니처: count(Integer, String, Long)
        // ✅ BillService 시그니처: list(Integer, String, Long, int, int)
        long totalL = billService.count(billingYyyymm, keyword, invoiceId);
        int total = (totalL > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalL;

        var rows = billService.list(billingYyyymm, keyword, invoiceId, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("bills", rows);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);

        if (invoiceId != null) {
            model.addAttribute("selectedInvoiceId", invoiceId);
        }

        return "bills";
    }

    @GetMapping("/payments")
    public String payments(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model
    ) {
        model.addAttribute("pageTitle", "Payments");
        model.addAttribute("activeMenu", "payments");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/payments");

        // payments는 invoice_id가 없으니 null로 고정
        Long invoiceId = null;

        long totalL = billService.count(billingYyyymm, keyword, invoiceId);
        int total = (totalL > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalL;

        var rows = billService.list(billingYyyymm, keyword, invoiceId, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("bills", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);

        return "payments";
    }

    @GetMapping("/sending")
    public String sending(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "invoice_id", required = false) Long invoiceId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "delivery_channel", required = false) String deliveryChannel,
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            Model model
    ) {
        String safeTab = (tab == null || tab.isBlank()) ? "status" : tab;

        model.addAttribute("pageTitle", "SENDING");
        model.addAttribute("activeMenu", "sending");

        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("invoice_id", invoiceId);
        model.addAttribute("status", status);
        model.addAttribute("delivery_channel", deliveryChannel);
        model.addAttribute("tab", safeTab);

        // 기본값(템플릿 안정)
        model.addAttribute("deliveryStatuses", java.util.List.of());
        model.addAttribute("deliveryHistories", java.util.List.of());
        model.addAttribute("deliverySummaries", java.util.List.of());

        // HISTORY 탭
        if ("history".equalsIgnoreCase(safeTab)) {
            if (invoiceId != null) {
                model.addAttribute("deliveryHistories", sendingService.history(invoiceId));
            }
            return "sending";
        }

        // SUMMARY 탭
        if ("summary".equalsIgnoreCase(safeTab)) {
            model.addAttribute("deliverySummaries", sendingService.summaries(billingYyyymm));
            return "sending";
        }

        // STATUS 탭
        int total = sendingService.count(billingYyyymm, status, deliveryChannel, null, invoiceId);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        int totalPages = (int) Math.ceil(total / (double) safeSize);

        model.addAttribute("total", total);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("totalPages", totalPages);

        model.addAttribute("deliveryStatuses",
                sendingService.list(billingYyyymm, status, deliveryChannel, null, invoiceId, safePage, safeSize));

        return "sending";
    }


    @GetMapping("/batch-jobs")
    public String batchJobs(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "attempt_id", required = false) Long attemptId,
            Model model
    ) {
        model.addAttribute("pageTitle", "Batch Jobs");
        model.addAttribute("activeMenu", "batch-jobs");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/batch-jobs");
        model.addAttribute("attempt_id", attemptId);

        // ⚠️ BatchJobsService 시그니처를 모름(listAttempts undefined).
        // 일단 템플릿이 안 깨지도록 키만 채움. 서비스 시그니처 확인 후 연결.
        model.addAttribute("statusStats", List.of());
        model.addAttribute("attemptRows", List.of());
        model.addAttribute("attemptDetail", null);
        model.addAttribute("failureRows", List.of());

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
