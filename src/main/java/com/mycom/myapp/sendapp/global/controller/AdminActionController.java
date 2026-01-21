package com.mycom.myapp.sendapp.global.controller;

import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminActionController {

    private final SendingService sendingService;

    public AdminActionController(SendingService sendingService) {
        this.sendingService = sendingService;
    }

    @PostMapping("/admin/actions/sending/resend-user")
    public String resendUser(
            @RequestParam("billing_yyyymm") Integer billingYyyymm,
            @RequestParam("users_id") Long usersId,
            @RequestParam(value = "failed_channel", required = false) String failedChannel,
            @RequestParam("resend_channel") String resendChannel,
            RedirectAttributes ra
    ) {
        int updated = sendingService.requestResendUser(billingYyyymm, usersId, failedChannel, resendChannel);
        ra.addAttribute("billing_yyyymm", billingYyyymm);
        ra.addAttribute("users_id", usersId);
        ra.addFlashAttribute("message", "READY 전환 요청: " + updated + "건");
        return "redirect:/sending/user";
    }

    @PostMapping("/admin/actions/sending/resend-bulk")
    public String resendBulk(
            @RequestParam("billing_yyyymm") Integer billingYyyymm,
            @RequestParam("failed_channel") String failedChannel,
            @RequestParam("resend_channel") String resendChannel,
            RedirectAttributes ra
    ) {
        int updated = sendingService.requestResendBulk(billingYyyymm, failedChannel, resendChannel);
        ra.addAttribute("billing_yyyymm", billingYyyymm);
        ra.addFlashAttribute("message", "READY 전환 요청: " + updated + "건");
        return "redirect:/sending";
    }
}
