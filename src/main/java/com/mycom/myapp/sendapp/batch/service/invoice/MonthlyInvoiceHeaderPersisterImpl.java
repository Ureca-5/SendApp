package com.mycom.myapp.sendapp.batch.service.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.batch.repository.invoice.MonthlyInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Repository 호출
 * 청구서 헤더 일괄 insert, 일괄 insert한 청구서 헤더의 usersId와 invoiceId 조회를 담당
 */
@RequiredArgsConstructor
@Service
public class MonthlyInvoiceHeaderPersisterImpl implements MonthlyInvoiceHeaderPersister {
    private final MonthlyInvoiceRepository monthlyInvoiceRepository;

    @Override
    public void batchInsertHeaders(List<MonthlyInvoiceRowDto> headers) {
        monthlyInvoiceRepository.batchInsert(headers);
    }

    @Override
    public Map<Long, Long> findInvoiceIdsByUsers(Integer targetYyyymm, List<Long> usersIds) {
        return monthlyInvoiceRepository.findInvoiceIdsByUsers(targetYyyymm, usersIds);
    }
}
