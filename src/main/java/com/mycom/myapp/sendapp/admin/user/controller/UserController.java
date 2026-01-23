package com.mycom.myapp.sendapp.admin.user.controller;

import com.mycom.myapp.sendapp.admin.user.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public String users(
            Model model,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "withdrawn", required = false) Boolean withdrawn,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "billing_yyyymm", required = false) Integer billingYyyymm,
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
        model.addAttribute("size", UserService.FIXED_SIZE);

        model.addAttribute("billing_yyyymm", billingYyyymm);
        model.addAttribute("keyword", keyword);
        model.addAttribute("email", email);
        model.addAttribute("phone", phone);
        model.addAttribute("withdrawn", withdrawn);

        return "admin/users";
    }
}
