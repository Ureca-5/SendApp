package com.mycom.myapp.sendapp.admin.batchjobs.service;

import com.mycom.myapp.sendapp.admin.batchjobs.dao.BatchJobsDao;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowDTO;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchFailureRowDTO;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class BatchJobsService {

    private final BatchJobsDao batchJobsDao;

    public BatchJobsService(BatchJobsDao batchJobsDao) {
        this.batchJobsDao = batchJobsDao;
    }

    public List<BatchJobsDao.StatusCountRow> statusStats(int billingYyyymm) {
        return batchJobsDao.statusStats(billingYyyymm);
    }

    public List<BatchAttemptRowDTO> listAttemptsRecent(int billingYyyymm, int limit) {
        return batchJobsDao.listAttemptsRecent(billingYyyymm, limit);
    }

    public BatchAttemptRowDTO readAttempt(long attemptId) {
        return batchJobsDao.readAttempt(attemptId);
    }

    /**
     * ⚠️ 실패 목록 테이블(정산 실패 이력)이 확정되기 전까지는 empty로 두는 게 안전.
     * DB 계약이 확정되면 DAO 쿼리를 채우면 됨.
     */
    public List<BatchFailureRowDTO> listFailures(long attemptId, int limit) {
        return batchJobsDao.listFailures(attemptId, limit);
    }
}
