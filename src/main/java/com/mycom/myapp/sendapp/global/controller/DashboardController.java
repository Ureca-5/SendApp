//package com.mycom.myapp.sendapp.global.controller;
//
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//
//import java.util.List;
//
//@Controller
//public class DashboardController {
//
//    @GetMapping("/")
//    public String dashboard(
//            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
//            Model model
//    ) {
//        model.addAttribute("pageTitle", "Dashboard");
//        model.addAttribute("activeMenu", "dashboard");
//        model.addAttribute("billing_yyyymm", billingYyyymm);
//        model.addAttribute("filterAction", "/");
//
//        // 기존 PageController가 비워둔 값 그대로 유지(대시보드 템플릿이 참조할 수 있음)
//        model.addAttribute("batchRecentAttempts", List.of());
//        model.addAttribute("batchLatestAttempt", null);
//
//        return "dashboard";
//    }
//}
