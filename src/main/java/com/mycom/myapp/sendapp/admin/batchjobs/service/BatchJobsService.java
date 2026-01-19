package com.mycom.myapp.sendapp.admin.batchjobs.service;

import com.mycom.myapp.sendapp.admin.batchjobs.dao.BatchJobsDao;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BatchJobsService {

    private final BatchJobsDao batchJobsDao;

    public BatchJobsService(BatchJobsDao batchJobsDao) {
        this.batchJobsDao = batchJobsDao;
    }

    public int countAttempts(Integer billingYyyymm) {
        return batchJobsDao.countAttempts(billingYyyymm);
    }

    public List<BatchAttemptRowDTO> listAttempts(Integer billingYyyymm, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int offset = safePage * safeSize;
        return batchJobsDao.listAttempts(billingYyyymm, safeSize, offset);
    }
}
