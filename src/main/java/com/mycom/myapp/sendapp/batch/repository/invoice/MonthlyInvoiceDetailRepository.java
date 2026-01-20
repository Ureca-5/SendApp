package com.mycom.myapp.sendapp.batch.repository.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceDetailRowDto;

import java.util.List;

public interface MonthlyInvoiceDetailRepository {
    /**
     * 상세 batch insert
     * - UK(invoice_id, invoice_category_id, billing_history_id)로 멱등성/중복 방지
     * - 중복 insert 시 DuplicateKeyException 발생 가능
     */
    int[] batchInsert(List<MonthlyInvoiceDetailRowDto> details);

    /**
     * 상세 batch insert (MySQL IGNORE)
     * - 중복/UK 충돌을 "무시"하고 계속 진행하고 싶을 때 사용
     * - 주의: 실제 오류도 같이 무시될 수 있어 사용은 신중히
     */
    int[] batchInsertIgnore(List<MonthlyInvoiceDetailRowDto> details);
}
