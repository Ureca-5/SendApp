package com.mycom.myapp.sendapp.batch.repository.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;

import java.util.List;
import java.util.Map;

public interface MonthlyInvoiceRepository {
    void batchInsert(List<MonthlyInvoiceRowDto> headers);

    Map<Long, Long> findInvoiceIdsByUsers(Integer targetYyyymm, List<Long> usersIds);
}
