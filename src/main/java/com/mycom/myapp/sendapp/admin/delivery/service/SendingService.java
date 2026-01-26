package com.mycom.myapp.sendapp.admin.delivery.service;

import com.mycom.myapp.sendapp.admin.delivery.dao.SendingDao;
import com.mycom.myapp.sendapp.admin.delivery.dto.DeliverySummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingHistoryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusRowDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SendingService {

    private final SendingDao sendingDao;

    public SendingService(SendingDao sendingDao) {
        this.sendingDao = sendingDao;
    }

    public int count(Integer billingYyyymm, String status, String deliveryChannel, Long usersId, Long invoiceId) {
        return sendingDao.count(billingYyyymm, status, deliveryChannel, usersId, invoiceId);
    }

    public List<SendingStatusRowDTO> list(Integer billingYyyymm, String status, String deliveryChannel,
                                          Long usersId, Long invoiceId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = safePage * safeSize;
        return sendingDao.list(billingYyyymm, status, deliveryChannel, usersId, invoiceId, safeSize, offset);
    }

    public List<SendingHistoryRowDTO> history(long invoiceId) {
        return sendingDao.history(invoiceId);
    }

    public List<DeliverySummaryRowDTO> summaries(Integer billingYyyymm) {
        return sendingDao.summaries(billingYyyymm);
    }
}
