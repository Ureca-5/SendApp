package com.mycom.myapp.sendapp.global.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mycom.myapp.sendapp.admin.user.service.UserService;

@Controller
public class PageController {
	private final UserService userService;
	public PageController(UserService userService) {
	    this.userService = userService;
	}
    @GetMapping("/")
    public String dashboard(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            Model model
    ) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("billing_yyyymm", billingYyyymm); // 전역 필터 값(없으면 null)
        model.addAttribute("filterAction", "/");             // 전역 필터 폼 action

        return "dashboard"; // templates/dashboard.html
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


    @GetMapping("/invoices")
    public String invoices(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            Model model
    ) {
        model.addAttribute("pageTitle", "Invoices");
        model.addAttribute("activeMenu", "invoices");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/invoices");

        return "invoices"; // templates/invoices.html
    }

    @GetMapping("/billing")
    public String billing(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            Model model
    ) {
        model.addAttribute("pageTitle", "Billing Histories");
        model.addAttribute("activeMenu", "billing");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/billing");

        return "billing"; // templates/billing.html
    }

    @GetMapping("/delivery")
    public String delivery(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            Model model
    ) {
        model.addAttribute("pageTitle", "Delivery");
        model.addAttribute("activeMenu", "delivery");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/delivery");

        return "delivery"; // templates/delivery.html
    }

    @GetMapping("/batch")
    public String batch(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            Model model
    ) {
        model.addAttribute("pageTitle", "Batch");
        model.addAttribute("activeMenu", "batch");
        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("filterAction", "/batch");

        return "batch"; // templates/batch.html
    }
}
