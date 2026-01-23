package com.mycom.myapp.sendapp.admin.delivery.controller;

import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SendingController {

    private final SendingService sendingService;

    public SendingController(SendingService sendingService) {
        this.sendingService = sendingService;
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

        // history 탭
        if ("history".equalsIgnoreCase(safeTab)) {
            model.addAttribute("deliveryHistories",
                    (invoiceId == null) ? List.of() : sendingService.history(invoiceId));
            model.addAttribute("deliveryStatuses", List.of());
            model.addAttribute("deliverySummaries", List.of());
            return "sending";
        }

        // summary 탭
        if ("summary".equalsIgnoreCase(safeTab)) {
            model.addAttribute("deliverySummaries", sendingService.summaries(billingYyyymm));
            model.addAttribute("deliveryStatuses", List.of());
            model.addAttribute("deliveryHistories", List.of());
            return "sending";
        }

        // status 탭
        int total = sendingService.count(billingYyyymm, status, deliveryChannel, null, invoiceId);
        int totalPages = (int) Math.ceil(total / (double) Math.max(size, 1));

        model.addAttribute("total", total);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", totalPages);

        model.addAttribute("deliveryStatuses",
                sendingService.list(billingYyyymm, status, deliveryChannel, null, invoiceId, page, size));
        model.addAttribute("deliveryHistories", List.of());
        model.addAttribute("deliverySummaries", List.of());

        return "sending";
    }
}
