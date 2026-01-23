package com.mycom.myapp.sendapp.admin.batchjobs.service;

import com.mycom.myapp.sendapp.admin.batchjobs.dao.BatchAttemptDao;
import com.mycom.myapp.sendapp.admin.batchjobs.dao.BatchjobsFailDao;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowVM;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchFailRowVM;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchJobStatusStatDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BatchJobsService {

    private final BatchAttemptDao attemptDao;
    private final BatchjobsFailDao failDao;

    public BatchJobsService(BatchAttemptDao attemptDao, BatchjobsFailDao failDao) {
        this.attemptDao = attemptDao;
        this.failDao = failDao;
    }

    public List<BatchJobStatusStatDTO> stats(int yyyymm) {
        return attemptDao.statsByStatus(yyyymm);
    }

    public List<BatchAttemptRowVM> recentAttempts(int yyyymm, int limit) {
        return attemptDao.listRecentWithLastFail(yyyymm, limit);
    }

    /**
     * fail 테이블은 target_yyyymm가 없으므로 attempt JOIN으로 월 필터링.
     * attemptId는 선택(optional).
     */
    public List<BatchFailRowVM> fails(int yyyymm, Long attemptId, int limit) {
        return failDao.listFailsByYyyymm(yyyymm, attemptId, limit);
    }
}
