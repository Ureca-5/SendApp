package com.mycom.myapp.sendapp.invoice.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;
import com.mycom.myapp.sendapp.invoice.dao.InvoiceEmailDao;
import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailDetailRawDTO;
import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailHeaderRawDTO;
import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailLineVM;
import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailVM;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    private final InvoiceEmailDao dao;
    private final ContactProtector protector;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KOREAN_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");

    /**
     * invoice_id로 조회 -> 메일 템플릿에 꽂을 ViewModel 생성
     * - name: maskedName(name)
     * - phone/email: 암호문(ENC) 가정, protector.maskedX(EncryptedString.of(enc))
     * - C 카테고리 metaText는 대체안(원천 조인 없음)
     *
     * (보안/마스킹 정책은 별도 처리한다 했으니)
     * - payment_info는 그대로 노출(혹은 이미 마스킹된 문자열 전제)
     */
    public InvoiceEmailVM buildView(long invoiceId) {
        InvoiceEmailHeaderRawDTO h = dao.loadHeader(invoiceId);
        List<InvoiceEmailDetailRawDTO> details = dao.loadDetails(invoiceId);

        // === Header/Top ===
        String billMonthText = formatBillMonth(h.billingYyyymm());

        String customerNameMasked = safeMaskedName(h.userName());
        String phoneMasked = safeMaskedPhone(h.userPhone());

        String totalAmountText = formatWon(nz(h.totalAmount()));

        // usage period: A/B(1,2,3) 기준으로 잡는 header 쿼리 결과 사용
        String usageStartText = formatKoreanDate(h.usageStartDate());
        String usageEndText = formatKoreanDate(h.usageEndDate());

        String billCreatedDateText = formatKoreanDate(h.createdAt());

        String paymentMethodText = safe(h.paymentMethod());
        String paymentInfoText = formatPaymentInfoForDisplay(h.paymentInfo());; // 마스킹/복호화는 너가 따로 처리

        // === Summary (A/B/C) ===
        String sumAAmountText = formatWon(nz(h.sumAAmount()));
        String sumBAmountText = formatWon(nz(h.sumBAmount()));
        String sumCAmountText = formatWon(nz(h.sumCAmount()));

        // === Group details ===
        List<InvoiceEmailLineVM> aDetails = new ArrayList<>();
        List<InvoiceEmailLineVM> bDetails = new ArrayList<>();
        List<InvoiceEmailLineVM> cDetails = new ArrayList<>();

        for (InvoiceEmailDetailRawDTO d : details) {
            InvoiceEmailLineVM vm = toLineVM(d);
            int cat = d.invoiceCategoryId() == null ? -1 : d.invoiceCategoryId();

            // 매핑: (1,3)=A / 2=B / 4=C
            if (cat == 2) bDetails.add(vm);
            else if (cat == 4) cDetails.add(vm);
            else if (cat == 1 || cat == 3) aDetails.add(vm);
            else aDetails.add(vm); // 정의 밖이면 A쪽으로 밀어넣음(정책에 맞게 바꿔도 됨)
        }

        return new InvoiceEmailVM(
                billMonthText,
                customerNameMasked,
                phoneMasked,
                totalAmountText,
                usageStartText,
                usageEndText,
                billCreatedDateText,
                paymentMethodText,
                paymentInfoText,
                sumAAmountText,
                sumBAmountText,
                sumCAmountText,
                aDetails,
                bDetails,
                cDetails
        );
    }

	private InvoiceEmailLineVM toLineVM(InvoiceEmailDetailRawDTO d) {
        String serviceNameText = safe(d.serviceName());

        // line amount: 원본 템플릿처럼 origin을 크게 보여주고 할인 표시 (없으면 total fallback)
        long origin = nz(d.originAmount());
        long total = nz(d.totalAmount());
        String lineAmountText = formatWon(origin > 0 ? origin : total);

        // discount text: >0일 때만 "할인 -#,###원"
        String discountTextOrNull = null;
        long discount = nz(d.discountAmount());
        if (discount > 0) {
            discountTextOrNull = "-" + formatNumber(discount) + "원";
        }

        // metaText:
        int cat = d.invoiceCategoryId() == null ? -1 : d.invoiceCategoryId();
        String metaText;
        if (cat == 4) {
            // C 대체안: "MM.DD 결제" or "MM.DD~MM.DD"
            metaText = formatMetaC(d.usageStartDate(), d.usageEndDate());
        } else {
            metaText = formatPeriod(d.usageStartDate(), d.usageEndDate());
        }

        return new InvoiceEmailLineVM(serviceNameText, metaText, lineAmountText, discountTextOrNull);
    }

    // =========================
    // Formatting
    // =========================

    private String formatBillMonth(Integer yyyymm) {
        if (yyyymm == null) return "";
        int y = yyyymm / 100;
        int m = yyyymm % 100;
        return y + "년 " + m + "월";
    }

    private String formatKoreanDate(Date date) {
        if (date == null) return "";
        LocalDate ld = date.toLocalDate();
        return ld.format(KOREAN_DATE);
    }

    private String formatKoreanDate(Timestamp ts) {
        if (ts == null) return "";
        LocalDate ld = ts.toInstant().atZone(KST).toLocalDate();
        return ld.format(KOREAN_DATE);
    }

    private String formatPeriod(Date start, Date end) {
        if (start == null && end == null) return "";
        LocalDate s = start != null ? start.toLocalDate() : null;
        LocalDate e = end != null ? end.toLocalDate() : null;

        if (s != null && e != null) return s.format(MONTH_DAY) + "~" + e.format(MONTH_DAY);
        if (s != null) return s.format(MONTH_DAY);
        return e.format(MONTH_DAY);
    }

    /**
     * - start==end면 "MM.DD 결제"
     * - 둘 다 있으면 "MM.DD~MM.DD"
     * - 하나만 있으면 "MM.DD 결제"
     */
    private String formatMetaC(Date start, Date end) {
        if (start != null && end != null) {
            LocalDate s = start.toLocalDate();
            LocalDate e = end.toLocalDate();
            if (s.equals(e)) return s.format(MONTH_DAY) + " 결제";
            return s.format(MONTH_DAY) + "~" + e.format(MONTH_DAY);
        }
        if (start != null) return start.toLocalDate().format(MONTH_DAY) + " 결제";
        if (end != null) return end.toLocalDate().format(MONTH_DAY) + " 결제";
        return "";
    }

    private String formatWon(long amount) {
        return formatNumber(amount) + "원";
    }

    private String formatNumber(long n) {
        return NumberFormat.getInstance(Locale.KOREA).format(n);
    }

    // =========================
    // Crypto masking (ContactProtector)
    // =========================

    private String safeMaskedName(String name) {
        if (name == null || name.isBlank()) return "";
        try {
            return protector.maskedName(name);
        } catch (Exception e) {
            // name은 원래 평문일 가능성이 높고, 여기서 예외 나면 그냥 빈값 처리
            return "";
        }
    }


    private String safeMaskedPhone(String encPhone) {
        if (encPhone == null || encPhone.isBlank()) return "";
        try {
            return protector.maskedPhone(EncryptedString.of(encPhone));
        } catch (Exception e) {
            // 암호문이 아니거나 키 설정 문제 등 -> 여기선 빈값 처리(정책에 맞게 수정 가능)
            return "";
        }
    }


    @SuppressWarnings("unused")
    private String safeMaskedEmail(String encEmail) {
        if (encEmail == null || encEmail.isBlank()) return "";
        try {
            return protector.maskedEmail(EncryptedString.of(encEmail));
        } catch (Exception e) {
            return "";
        }
    }
    private String formatPaymentInfoForDisplay(String paymentInfo) {
        if (paymentInfo == null || paymentInfo.isBlank()) return "";

        String s = paymentInfo.trim();

        // CARD_TOKEN_****-****-****-5307 -> ****-****-****-5307
        if (s.startsWith("CARD_TOKEN_")) {
            return s.substring("CARD_TOKEN_".length());
        }

        // BANK_TOKEN_651_668830 -> BANK(651) ***830  (또는 ***8830 등 정책)
        if (s.startsWith("BANK_TOKEN_")) {
            String rest = s.substring("BANK_TOKEN_".length()); // 651_668830
            String[] parts = rest.split("_", -1);

            String bankCode = parts.length >= 1 ? parts[0] : "";
            String accountLike = parts.length >= 2 ? parts[1] : "";

            String tail = tailDigits(accountLike, 3); // 마지막 3자리만
            if (tail.isEmpty()) tail = "???";

            // 은행명 매핑 없으면 코드 표시
            if (!bankCode.isBlank()) {
                return "******-***" + tail;
            }
            return "***" + tail;
        }

        // 그 외: 이미 마스킹된 문자열이면 그대로, 아니면 최소 마스킹
        return s;
    }

    private String tailDigits(String value, int n) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        if (digits.length() <= n) return digits;
        return digits.substring(digits.length() - n);
    }



    // =========================
    // Null helpers
    // =========================

    private long nz(Long v) {
        return v == null ? 0L : v;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
 