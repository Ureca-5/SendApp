// src/main/java/com/mycom/myapp/sendapp/admin/dashboard/controller/DashboardController.java
package com.mycom.myapp.sendapp.admin.dashboard.controller;

import com.mycom.myapp.sendapp.admin.dashboard.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
            @RequestParam(value = "limit", required = false, defaultValue = "8") Integer limit,
            Model model
    ) {
        // limit 방어: 과도한 값으로 DB 부하 유발 방지 (1~50 범위로 제한)
        int safeLimit = (limit == null ? 8 : limit);
        if (safeLimit < 1) safeLimit = 1;
        if (safeLimit > 50) safeLimit = 50;

        var view = dashboardService.load(billingYyyymm, safeLimit);

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("activeMenu", "dashboard");

        model.addAttribute("billing_yyyymm", view.billingYyyymm());

        // KPI
        model.addAttribute("billingTargetCount", view.billingTargetCount());
        model.addAttribute("batchOkCount", view.batchOkCount());
        model.addAttribute("batchFailCount", view.batchFailCount());

        model.addAttribute("sendingTargetCount", view.sendingTargetCount());
        model.addAttribute("sendingSentCount", view.sendingSentCount());
        model.addAttribute("sendingFailedCount", view.sendingFailedCount());

        // Recent
        model.addAttribute("batchRecentAttempts", view.batchRecentAttempts());
        model.addAttribute("batchLatestAttempt", view.batchLatestAttempt());
        model.addAttribute("recentSendingHistory", view.recentSendingHistory());

        return "admin/dashboard";
    }
}
