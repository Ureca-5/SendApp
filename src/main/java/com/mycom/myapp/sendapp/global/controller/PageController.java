package com.mycom.myapp.sendapp.global.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String home(Model model) {
        // 데이터 전달 (Key, Value)
        model.addAttribute("message", "Thymeleaf 설정 완료");
        
        // 리턴값은 templates 폴더 내의 파일명(확장자 제외)이어야 함
        return "index"; 
    }
}