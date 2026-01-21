package com.mycom.myapp.sendapp.admin.delivery.service;

import com.mycom.myapp.sendapp.admin.delivery.dao.SendingDao;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingHistoryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusSummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingChannelStatusSummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingKpiDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingRecentHistoryRowDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SendingService {

    private final SendingDao sendingDao;

    public SendingService(SendingDao sendingDao) {
        this.sendingDao = sendingDao;
    }

    public int count(Integer billingYyyymm, String status, String channel, Long usersId, Long invoiceId) {
        return sendingDao.count(billingYyyymm, status, channel, usersId, invoiceId);
    }

    public List<SendingStatusRowDTO> list(Integer billingYyyymm, String status, String channel, Long usersId, Long invoiceId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = safePage * safeSize;
        return sendingDao.list(billingYyyymm, status, channel, usersId, invoiceId, safeSize, offset);
    }

    public SendingKpiDTO kpi(Integer billingYyyymm) {
    return sendingDao.kpi(billingYyyymm);
}

public List<SendingStatusSummaryRowDTO> statusSummary(Integer billingYyyymm) {
    return sendingDao.statusSummary(billingYyyymm);
}

public List<SendingChannelStatusSummaryRowDTO> channelStatusSummary(Integer billingYyyymm) {
    return sendingDao.channelStatusSummary(billingYyyymm);
}

public List<SendingRecentHistoryRowDTO> recentHistory(Integer billingYyyymm, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 50);
    return sendingDao.recentHistory(billingYyyymm, safeLimit);
}

public List<SendingStatusRowDTO> listByUser(Integer billingYyyymm, long usersId) {
    return sendingDao.listByUser(billingYyyymm, usersId);
}

public int requestResendUser(Integer billingYyyymm, long usersId, String failedChannel, String resendChannel) {
    return sendingDao.requestResendUser(billingYyyymm, usersId, failedChannel, resendChannel);
}

public int requestResendBulk(Integer billingYyyymm, String failedChannel, String resendChannel) {
    return sendingDao.requestResendBulk(billingYyyymm, failedChannel, resendChannel);
}

public List<SendingHistoryRowDTO> history(long invoiceId) {
        return sendingDao.history(invoiceId);
    }
}
