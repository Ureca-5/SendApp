package com.mycom.myapp.sendapp.batch.controller;

import com.mycom.myapp.sendapp.batch.scheduler.MonthlyInvoiceBatchScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch-test")
@RequiredArgsConstructor
public class MonthlyInvoiceTestController {
    private final MonthlyInvoiceBatchScheduler monthlyInvoiceBatchScheduler;

    @PostMapping("/test/{targetYyyymm}")
    public void test(@PathVariable("targetYyyymm") Integer targetYyyymm) {
        monthlyInvoiceBatchScheduler.testMonthlyInvoiceBatch(targetYyyymm);
    }


}
