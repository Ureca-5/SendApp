package com.mycom.myapp.sendapp.batch.processor;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.batch.dto.UserBillingDayDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;

/**
 * Processor:
 * - Reader가 제공한 usersId/billingDay 하나당 MonthlyInvoiceRowDto 1건 생성
 * - DB 접근 없음
 *
 * writer에서 상세, 원천 데이터 조회 및 반영이 수행됩니다.
 */
@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class InvoiceSettlementProcessor implements ItemProcessor<UserBillingDayDto, MonthlyInvoiceRowDto> {
    /**
     * 정산 대상 월 (YYYYMM)
     * - StepScope + late binding
     */
    @Value("#{jobParameters['targetYyyymm']}")
    private Integer targetYyyymm;

    @Override
    public MonthlyInvoiceRowDto process(UserBillingDayDto input) {
        if (input == null || input.getUsersId() == null) {
            return null; // null이면 writer로 넘어가지 않음
        }
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("jobParameters['targetYyyymm'] is required.");
        }
        Integer billingDay = input.getBillingDay();

        // 현재 시각
        LocalDateTime now = LocalDateTime.now();

        // dueDate 계산: billingDay가 말일보다 크면 말일로 보정
        YearMonth ym = YearMonth.of(targetYyyymm / 100, targetYyyymm % 100);
        int lastDay = ym.lengthOfMonth();
        int day = (billingDay == null || billingDay < 1) ? lastDay : Math.min(billingDay, lastDay);
        LocalDate dueDate = LocalDate.of(ym.getYear(), ym.getMonth(), day);

        // 기본 헤더 DTO 생성
        return MonthlyInvoiceRowDto.builder()
                .invoiceId(null)                  // writer에서 insert 후 채워넣습니다
                .usersId(input.getUsersId())
                .billingYyyymm(targetYyyymm)
                .totalPlanAmount(0L)
                .totalAddonAmount(0L)
                .totalEtcAmount(0L)
                .totalDiscountAmount(0L)
                .totalAmount(0L)
                .settlementSuccess(true)        // 정산 성공 유무: 초기 상태 true
                .createdAt(now)                  // writer가 원하는 경우 덮어쓰기 가능
                .expiredAt(now.toLocalDate().plusYears(5)) // 만료 일자: 생성 일자 + 5년
                .dueDate(dueDate)
                .build();
    }
}
