package com.mycom.myapp.sendapp.global.controller;

import com.mycom.myapp.sendapp.admin.invoice.dto.BillRowViewDTO;
import com.mycom.myapp.sendapp.admin.invoice.dto.InvoiceDetailRowViewDTO;
import com.mycom.myapp.sendapp.admin.invoice.service.BillService;
import com.mycom.myapp.sendapp.admin.user.service.UserService;
import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
import com.mycom.myapp.sendapp.admin.batchjobs.service.BatchJobsService;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        return "dashboard";
    }

    @GetMapping("/users")
    public String users(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "withdrawn", required = false) Boolean withdrawn,
            Model model
    ) {
        model.addAttribute("pageTitle", "Users");
        model.addAttribute("activeMenu", "users");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/users");

        int total = userService.count(keyword, withdrawn);
        var users = userService.list(keyword, withdrawn, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("users", users);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);
        model.addAttribute("withdrawn", withdrawn);

        return "users";
    }

    // ✅ 청구서 발행 내역
    @GetMapping("/bills")
    public String bills(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "keyword", required = false) String keyword, // users_id or name
            @RequestParam(value = "invoice_id", required = false) Long invoiceId, // 상세 선택(옵션)
            Model model
    ) {
        model.addAttribute("pageTitle", "Bills");
        model.addAttribute("activeMenu", "bills");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/bills");

        long totalL = billService.count(billingYyyymm, keyword);
        int total = (totalL > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalL;

        List<BillRowViewDTO> rows = billService.list(billingYyyymm, keyword, page, size);

        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("bills", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);


        if (invoiceId != null) {
            List<InvoiceDetailRowViewDTO> detail = billService.details(invoiceId);
            model.addAttribute("selectedInvoiceId", invoiceId);
            model.addAttribute("details", detail);
        }

        return "bills";
    }

    // ✅ 수납/미납 관리 (ERD에 납부상태가 없으니 bills 재사용 수준)
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

        long total = billService.count(billingYyyymm, keyword);
        var rows = billService.list(billingYyyymm, keyword, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("bills", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);

        // templates/payments.html 에서 bills 테이블만 보여주면 됨
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
            @RequestParam(value = "invoice_id", required = false) Long invoiceId,
            Model model
    ) {
        model.addAttribute("pageTitle", "Sending");
        model.addAttribute("activeMenu", "sending");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/sending");

        int total = sendingService.count(billingYyyymm, status, channel);
        var rows = sendingService.list(billingYyyymm, status, channel, page, size);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("sendingRows", rows);
        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("status", status);
        model.addAttribute("channel", channel);

        if (invoiceId != null) {
            model.addAttribute("selectedInvoiceId", invoiceId);
            model.addAttribute("historyRows", sendingService.history(invoiceId));
        }

        return "sending";
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
    public String invoicesRedirect() { return "redirect:/bills"; }

    @GetMapping("/delivery")
    public String deliveryRedirect() { return "redirect:/sending"; }

    @GetMapping("/batch")
    public String batchRedirect() { return "redirect:/batch-jobs"; }
}
