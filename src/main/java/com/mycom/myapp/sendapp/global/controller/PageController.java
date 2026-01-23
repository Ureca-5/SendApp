//package com.mycom.myapp.sendapp.global.controller;
//
//import com.mycom.myapp.sendapp.admin.batchjobs.service.BatchJobsService;
//import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
//import com.mycom.myapp.sendapp.admin.invoice.service.BillService;
//import com.mycom.myapp.sendapp.admin.user.service.UserService;
//
//import java.util.List;
//
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.util.UriComponentsBuilder;
//
//@Controller
//public class PageController {
//
//    private final UserService userService;
//    private final BillService billService;
//    private final SendingService sendingService;
//    private final BatchJobsService batchJobsService;
//
//    public PageController(
//            UserService userService,
//            BillService billService,
//            SendingService sendingService,
//            BatchJobsService batchJobsService
//    ) {
//        this.userService = userService;
//        this.billService = billService;
//        this.sendingService = sendingService;
//        this.batchJobsService = batchJobsService;
//    }
//
// // (기존 PageController.java 안에)
// // @GetMapping("/") 메서드를 아래로 교체
// @GetMapping("/")
// public String root() {
//     return "redirect:/dashboard";
// }
//
//
////    @GetMapping("/users")
////    public String users(
////            Model model,
////            @RequestParam(value = "keyword", required = false) String keyword,
////            @RequestParam(value = "email", required = false) String email,
////            @RequestParam(value = "phone", required = false) String phone,
////            @RequestParam(value = "withdrawn", required = false) Boolean withdrawn,
////            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
////            @RequestParam(value = "billing_yyyymm", required = false) Integer billing_yyyymm,
////            @RequestParam(value = "searched", required = false, defaultValue = "0") Integer searched
////    ) {
////        int p = (page == null) ? 0 : Math.max(page, 0);
////        boolean isSearched = (searched != null && searched == 1);
////
////        var users = userService.list(isSearched, keyword, email, phone, withdrawn, p);
////        int total = isSearched ? userService.countIfNeeded(keyword, email, phone, withdrawn) : 0;
////
////        model.addAttribute("pageTitle", "Users");
////        model.addAttribute("activeMenu", "users");
////        model.addAttribute("filterAction", "/users");
////
////        model.addAttribute("users", users);
////        model.addAttribute("total", total);
////        model.addAttribute("searched", isSearched);
////        model.addAttribute("page", p);
////        model.addAttribute("size", com.mycom.myapp.sendapp.admin.user.service.UserService.FIXED_SIZE);
////
////        model.addAttribute("billing_yyyymm", billing_yyyymm);
////        model.addAttribute("keyword", keyword);
////        model.addAttribute("email", email);
////        model.addAttribute("phone", phone);
////        model.addAttribute("withdrawn", withdrawn);
////
////        return "users";
////    }
////
////    /**
////     * ✅ BILLS 화면 (bills.html) 전용: Attempts / Invoices / Failures
////     * - HTML 수정 없이, 모델 키/파라미터 계약을 맞춤
////     */
////    @GetMapping("/bills")
////    public String bills(
////            @RequestParam(value = "tab", required = false) String tab,
////            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
////
////            // Attempts 탭 필터
////            @RequestParam(value = "attempt_id", required = false) Long attemptId,
////            @RequestParam(value = "execution_status", required = false) String executionStatus,
////
////            // Invoices 탭 필터
////            @RequestParam(value = "keyword", required = false) String keyword,
////            @RequestParam(value = "invoice_id", required = false) Long invoiceId,
////
////            // Failures 탭 필터
////            @RequestParam(value = "error_code", required = false) String errorCode,
////
////            @RequestParam(value = "page", defaultValue = "0") int page,
////            @RequestParam(value = "size", defaultValue = "50") int size,
////            Model model
////    ) {
////        // ---- 공통(템플릿 상단/필터 공통)
////        String safeTab = normalizeTab(tab);
////        int safePage = Math.max(0, page);
////        int safeSize = clamp(size, 1, 200); // 서비스와 동일한 상한(200) 유지
////
////        model.addAttribute("pageTitle", "Bills");
////        model.addAttribute("activeMenu", "bills");
////        model.addAttribute("filterAction", "/bills");
////
////        model.addAttribute("tab", safeTab);
////        model.addAttribute("currentTabLabel", tabLabel(safeTab));
////
////        model.addAttribute("billing_yyyymm", billingYyyymm);
////        model.addAttribute("page", safePage);
////        model.addAttribute("size", safeSize);
////
////        // 필터 값들(탭 전환/페이저 링크 유지용)
////        model.addAttribute("attempt_id", attemptId);
////        model.addAttribute("execution_status", executionStatus);
////        model.addAttribute("keyword", keyword);
////        model.addAttribute("invoice_id", invoiceId);
////        model.addAttribute("error_code", errorCode);
////
////        // ---- 탭별 데이터 로딩
////        if ("attempts".equals(safeTab)) {
////            int total = billService.countAttempts(billingYyyymm, blankToNull(executionStatus));
////            int totalPages = (int) Math.ceil(total / (double) safeSize);
////
////            var attempts = billService.listAttempts(billingYyyymm, blankToNull(executionStatus), safePage, safeSize);
////
////            model.addAttribute("total", total);
////            model.addAttribute("totalPages", totalPages);
////            model.addAttribute("attempts", attempts);
////
////            // 우측 상세: attempt_id가 있으면 상세만 조회 (리스트는 월+상태 필터만 지원)
////            if (attemptId != null) {
////                model.addAttribute("selectedAttempt", billService.findAttempt(attemptId));
////            } else {
////                model.addAttribute("selectedAttempt", null);
////            }
////
////            // 다른 탭 키는 템플릿에서 안 쓰지만, 안전하게 빈 값 세팅(디버깅 편의)
////            model.addAttribute("invoices", List.of());
////            model.addAttribute("selectedInvoice", null);
////            model.addAttribute("invoiceDetails", List.of());
////            model.addAttribute("fails", List.of());
////
////            return "bills";
////        }
////
////        if ("failures".equals(safeTab)) {
////            int total = billService.countFailures(billingYyyymm, attemptId, blankToNull(errorCode));
////            int totalPages = (int) Math.ceil(total / (double) safeSize);
////
////            var fails = billService.listFailures(billingYyyymm, attemptId, blankToNull(errorCode), safePage, safeSize);
////
////            model.addAttribute("total", total);
////            model.addAttribute("totalPages", totalPages);
////            model.addAttribute("fails", fails);
////
////            // 다른 탭 키 안전 세팅
////            model.addAttribute("attempts", List.of());
////            model.addAttribute("selectedAttempt", null);
////            model.addAttribute("invoices", List.of());
////            model.addAttribute("selectedInvoice", null);
////            model.addAttribute("invoiceDetails", List.of());
////
////            return "bills";
////        }
////
////        // default: invoices
////        long totalL = billService.countInvoices(billingYyyymm, blankToNull(keyword), invoiceId);
////        long totalPagesL = (long) Math.ceil(totalL / (double) safeSize);
////
////        var invoices = billService.listInvoices(billingYyyymm, blankToNull(keyword), invoiceId, safePage, safeSize);
////
////        model.addAttribute("total", totalL);
////        model.addAttribute("totalPages", (int) Math.min(Integer.MAX_VALUE, totalPagesL));
////        model.addAttribute("invoices", invoices);
////
////        if (invoiceId != null) {
////            model.addAttribute("selectedInvoice", billService.findInvoiceView(invoiceId));
////            model.addAttribute("invoiceDetails", billService.invoiceDetails(invoiceId));
////        } else {
////            model.addAttribute("selectedInvoice", null);
////            model.addAttribute("invoiceDetails", List.of());
////        }
////
////        // 다른 탭 키 안전 세팅
////        model.addAttribute("attempts", List.of());
////        model.addAttribute("selectedAttempt", null);
////        model.addAttribute("fails", List.of());
////
////        return "bills";
////    }
////
////    /**
////     * 기존 payments가 billService.count/list를 호출하던 부분은 깨질 확률이 높아서
////     * 최소한 "invoices 목록"으로라도 동작하게 맞춤(템플릿 요구는 프로젝트마다 다름).
////     */
////    @GetMapping("/payments")
////    public String payments(
////            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
////            @RequestParam(value = "page", defaultValue = "0") int page,
////            @RequestParam(value = "size", defaultValue = "50") int size,
////            @RequestParam(value = "keyword", required = false) String keyword,
////            Model model
////    ) {
////        int safePage = Math.max(0, page);
////        int safeSize = clamp(size, 1, 200);
////
////        model.addAttribute("pageTitle", "Payments");
////        model.addAttribute("activeMenu", "payments");
////        model.addAttribute("billing_yyyymm", billingYyyymm);
////        model.addAttribute("filterAction", "/payments");
////        model.addAttribute("keyword", keyword);
////        model.addAttribute("page", safePage);
////        model.addAttribute("size", safeSize);
////
////        // payments는 invoice_id가 없다고 가정하고 invoice 헤더 목록만 보여줌
////        long totalL = billService.countInvoices(billingYyyymm, blankToNull(keyword), null);
////        long totalPagesL = (long) Math.ceil(totalL / (double) safeSize);
////        var rows = billService.listInvoices(billingYyyymm, blankToNull(keyword), null, safePage, safeSize);
////
////        model.addAttribute("bills", rows);
////        model.addAttribute("total", totalL);
////        model.addAttribute("totalPages", (int) Math.min(Integer.MAX_VALUE, totalPagesL));
////
////        return "payments";
////    }
////
////    @GetMapping("/sending")
////    public String sending(
////            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
////            @RequestParam(value = "users_id", required = false) Long usersId,
////            @RequestParam(value = "page", defaultValue = "0") int page,
////            @RequestParam(value = "size", defaultValue = "50") int size,
////            @RequestParam(value = "status", required = false) String status,
////            @RequestParam(value = "channel", required = false) String channel,
////            @RequestParam(value = "invoice_id", required = false) Long invoiceId,
////            Model model
////    ) {
////        model.addAttribute("pageTitle", "Sending");
////        model.addAttribute("activeMenu", "sending");
////        model.addAttribute("billing_yyyymm", billingYyyymm);
////        model.addAttribute("users_id", usersId);
////        model.addAttribute("filterAction", "/sending");
////        model.addAttribute("page", page);
////        model.addAttribute("size", size);
////        model.addAttribute("status", status);
////        model.addAttribute("channel", channel);
////
////        model.addAttribute("statusStats", List.of());
////        model.addAttribute("channelStatusStats", List.of());
////        model.addAttribute("userDeliveryRows", List.of());
////
////        int total = sendingService.count(billingYyyymm, status, channel, usersId, invoiceId);
////        var rows = sendingService.list(billingYyyymm, status, channel, usersId, invoiceId, page, size);
////        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));
////
////        model.addAttribute("sendingRows", rows);
////        model.addAttribute("total", total);
////        model.addAttribute("totalPages", totalPages);
////
////        if (invoiceId != null) {
////            model.addAttribute("selectedInvoiceId", invoiceId);
////            model.addAttribute("historyRows", sendingService.history(invoiceId));
////        }
////
////        return "sending";
////    }
////
////    @GetMapping("/batch-jobs")
////    public String batchJobs(
////            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
////            @RequestParam(value = "attempt_id", required = false) Long attemptId,
////            Model model
////    ) {
////        model.addAttribute("pageTitle", "Batch Jobs");
////        model.addAttribute("activeMenu", "batch-jobs");
////        model.addAttribute("billing_yyyymm", billingYyyymm);
////        model.addAttribute("filterAction", "/batch-jobs");
////        model.addAttribute("attempt_id", attemptId);
////
////        model.addAttribute("statusStats", List.of());
////        model.addAttribute("attemptRows", List.of());
////        model.addAttribute("attemptDetail", null);
////        model.addAttribute("failureRows", List.of());
////
////        return "batch-jobs";
////    }
////
////    // ---- 호환 redirect (기존 라우트)
////    @GetMapping("/invoices")
////    public String invoicesRedirect(
////            @RequestParam(value = "users_id", required = false) Long usersId,
////            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm
////    ) {
////        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/bills");
////        b.queryParam("tab", "invoices");
////        if (billingYyyymm != null) b.queryParam("billing_yyyymm", billingYyyymm);
////        if (usersId != null) b.queryParam("keyword", usersId);
////        return "redirect:" + b.toUriString();
////    }
////
////    @GetMapping("/delivery")
////    public String deliveryRedirect() { return "redirect:/sending"; }
////
////    @GetMapping("/batch")
////    public String batchRedirect() { return "redirect:/batch-jobs"; }
////
////    // =========================
////    // helpers
////    // =========================
////
////    private static String normalizeTab(String tab) {
////        if (tab == null || tab.isBlank()) return "attempts";
////        String t = tab.trim().toLowerCase();
////        if (t.equals("attempts") || t.equals("invoices") || t.equals("failures")) return t;
////        return "attempts";
////    }
////
////    private static String tabLabel(String tab) {
////        return switch (tab) {
////            case "attempts" -> "Attempts";
////            case "invoices" -> "Invoices";
////            case "failures" -> "Failures";
////            default -> "Attempts";
////        };
////    }
////
////    private static int clamp(int v, int min, int max) {
////        return Math.max(min, Math.min(max, v));
////    }
////
////    private static String blankToNull(String s) {
////        if (s == null) return null;
////        String t = s.trim();
////        return t.isEmpty() ? null : t;
////    }
//}
