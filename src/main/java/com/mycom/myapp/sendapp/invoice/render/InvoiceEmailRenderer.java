package com.mycom.myapp.sendapp.invoice.render;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailVM;
import com.mycom.myapp.sendapp.invoice.service.InvoiceEmailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InvoiceEmailRenderer {

    private final SpringTemplateEngine templateEngine;
    private final InvoiceEmailService invoiceEmailService;

    public String renderHtml(long invoiceId) {
        InvoiceEmailVM vm = invoiceEmailService.buildView(invoiceId);

        Context ctx = new Context(Locale.KOREA);
        ctx.setVariable("billMonthText", vm.billMonthText());
        ctx.setVariable("customerNameMasked", vm.customerNameMasked());
        ctx.setVariable("phoneMasked", vm.phoneMasked());
        ctx.setVariable("totalAmountText", vm.totalAmountText());
        ctx.setVariable("usageStartText", vm.usageStartText());
        ctx.setVariable("usageEndText", vm.usageEndText());
        ctx.setVariable("billCreatedDateText", vm.billCreatedDateText());
        ctx.setVariable("paymentMethodText", vm.paymentMethodText());
        ctx.setVariable("paymentInfoText", vm.paymentInfoText());
        ctx.setVariable("sumAAmountText", vm.sumAAmountText());
        ctx.setVariable("sumBAmountText", vm.sumBAmountText());
        ctx.setVariable("sumCAmountText", vm.sumCAmountText());
        ctx.setVariable("aDetails", vm.aDetails());
        ctx.setVariable("bDetails", vm.bDetails());
        ctx.setVariable("cDetails", vm.cDetails());

        // templates/email/invoice-email.html (네가 만든 HTML 파일명)
        return templateEngine.process("sendingTemplates/email-preview", ctx);

    }
}
