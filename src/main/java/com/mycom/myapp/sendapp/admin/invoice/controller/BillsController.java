package com.mycom.myapp.sendapp.admin.invoice.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mycom.myapp.sendapp.admin.invoice.service.BillService;
import com.mycom.myapp.sendapp.invoice.render.InvoiceEmailRenderer;

@Controller
public class BillsController {

    private final BillService billService;
    private final InvoiceEmailRenderer invoiceEmailRenderer;

    public BillsController(BillService billService, InvoiceEmailRenderer invoiceEmailRenderer) {
        this.billService = billService;
        this.invoiceEmailRenderer = invoiceEmailRenderer;
    }

    @GetMapping("/bills")
    public String bills(
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,

            // Attempts 탭 제거로 execution_status 삭제됨
            // 단, attempt_id는 Failures 탭 필터링에 사용되므로 유지
            @RequestParam(value = "attempt_id", required = false) Long attemptId,

            // Invoices 탭 필터
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "invoice_id", required = false) Long invoiceId,

            // Failures 탭 필터
            @RequestParam(value = "error_code", required = false) String errorCode,

            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            Model model
    ) {
        // 1. 탭 정규화 (기본값: invoices)
        String safeTab = normalizeTab(tab);
        int safePage = Math.max(0, page);
        int safeSize = clamp(size, 1, 200);

        model.addAttribute("pageTitle", "Bills");
        model.addAttribute("activeMenu", "bills");
        model.addAttribute("filterAction", "/bills");

        model.addAttribute("tab", safeTab);
        model.addAttribute("currentTabLabel", tabLabel(safeTab));

        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);

        // 2. 필터 값 유지 (execution_status 제외)
        model.addAttribute("attempt_id", attemptId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("invoice_id", invoiceId);
        model.addAttribute("error_code", errorCode);

        // 3. Failures 탭 로직
        if ("failures".equals(safeTab)) {
            int total = billService.countFailures(billingYyyymm, attemptId, blankToNull(errorCode));
            int totalPages = (int) Math.ceil(total / (double) safeSize);
            var fails = billService.listFailures(billingYyyymm, attemptId, blankToNull(errorCode), safePage, safeSize);

            model.addAttribute("total", total);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("fails", fails);

            // 다른 탭 데이터 초기화
            model.addAttribute("invoices", List.of());
            model.addAttribute("selectedInvoice", null);
            model.addAttribute("invoiceDetails", List.of());

            return "admin/bills";
        }

        // 4. Default: Invoices 탭 로직
        // (normalizeTab에서 attempts가 들어오더라도 invoices로 변환됨)
        long total = billService.countInvoices(billingYyyymm, blankToNull(keyword), invoiceId);
        int totalPages = (int) Math.ceil(total / (double) safeSize);
        var invoices = billService.listInvoices(billingYyyymm, blankToNull(keyword), invoiceId, safePage, safeSize);

        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("invoices", invoices);

        if (invoiceId != null) {
            model.addAttribute("selectedInvoice", billService.findInvoiceView(invoiceId));
            model.addAttribute("invoiceDetails", billService.invoiceDetails(invoiceId));
        } else {
            model.addAttribute("selectedInvoice", null);
            model.addAttribute("invoiceDetails", List.of());
        }

        model.addAttribute("fails", List.of());

        return "admin/bills";
    }

    // 탭 정규화 로직 변경: attempts -> invoices 리다이렉트 처리
    private static String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) return "invoices";
        String t = tab.trim().toLowerCase();
        // attempts가 들어와도 invoices로 처리 (혹은 invoices, failures만 허용)
        if (t.equals("invoices") || t.equals("failures")) return t;
        return "invoices";
    }

    // 라벨 매핑 변경
    private static String tabLabel(String tab) {
        return switch (tab) {
            case "invoices" -> "Invoices";
            case "failures" -> "Failures";
            default -> "Invoices";
        };
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
    
    @GetMapping(value = "/bills/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@RequestParam("invoice_id") Long invoiceId) {
        if (invoiceId == null || invoiceId <= 0) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("invalid invoice_id");
        }

        String html = invoiceEmailRenderer.renderHtml(invoiceId);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Cache-Control", "no-store")
                .body(html);
    }
}