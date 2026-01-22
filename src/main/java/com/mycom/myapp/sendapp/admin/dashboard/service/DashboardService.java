// src/main/java/com/mycom/myapp/sendapp/admin/dashboard/service/DashboardService.java
package com.mycom.myapp.sendapp.admin.dashboard.service;

import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowVM;
import com.mycom.myapp.sendapp.admin.batchjobs.service.BatchJobsService;
import com.mycom.myapp.sendapp.admin.dashboard.dao.DashboardDao;
import com.mycom.myapp.sendapp.admin.dashboard.dto.DashboardBatchAttemptVM;
import com.mycom.myapp.sendapp.admin.dashboard.dto.RecentSendingHistoryVM;
import com.mycom.myapp.sendapp.admin.delivery.service.SendingService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class DashboardService {

    private final BatchJobsService batchJobsService;
    private final SendingService sendingService;
    private final DashboardDao dashboardDao;

    public DashboardService(
            BatchJobsService batchJobsService,
            SendingService sendingService,
            DashboardDao dashboardDao
    ) {
        this.batchJobsService = batchJobsService;
        this.sendingService = sendingService;
        this.dashboardDao = dashboardDao;
    }

    public int resolveDefaultYyyymm(Integer billingYyyymm) {
        if (billingYyyymm != null) return billingYyyymm;
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return now.getYear() * 100 + now.getMonthValue();
    }

    public DashboardView load(Integer billingYyyymm, int recentLimit) {
        int yyyymm = 202512;//resolveDefaultYyyymm(billingYyyymm); 
        //임시로 202512 yyyymm 수정하는 로직 추가예정
        
        int limit = Math.max(3, Math.min(recentLimit, 50));

        // ===== Batch (최근 attempt 목록 + 최신 attempt 1건으로 KPI) =====
        List<BatchAttemptRowVM> recentAttemptsRaw = batchJobsService.recentAttempts(yyyymm, limit);

        List<DashboardBatchAttemptVM> recentAttempts = recentAttemptsRaw.stream()
                .map(this::toVm)
                .toList();

        DashboardBatchAttemptVM latest = recentAttempts.isEmpty() ? null : recentAttempts.get(0);

        long billingTargetCount = (latest != null && latest.successCount() != null && latest.failCount() != null)
                ? (latest.successCount() + latest.failCount())
                : (latest != null && latest.successCount() != null ? latest.successCount() : 0);

        long batchOkCount = latest != null && latest.successCount() != null ? latest.successCount() : 0;
        long batchFailCount = latest != null && latest.failCount() != null ? latest.failCount() : 0;

        // ===== Sending KPI =====
        long sendingTargetCount = sendingService.count(yyyymm, null, null, null, null);
        long sendingSentCount = sendingService.count(yyyymm, "SENT", null, null, null);
        long sendingFailedCount = sendingService.count(yyyymm, "FAILED", null, null, null);

        // ===== Recent Sending History =====
        List<RecentSendingHistoryVM> recentSendingHistory =
                dashboardDao.recentSendingHistory(yyyymm, limit);

        return new DashboardView(
                yyyymm,
                billingTargetCount,
                batchOkCount,
                batchFailCount,
                sendingTargetCount,
                sendingSentCount,
                sendingFailedCount,
                recentAttempts,
                latest,
                recentSendingHistory
        );
    }

    private DashboardBatchAttemptVM toVm(BatchAttemptRowVM r) {
        // startedAt를 화면에서 그대로 쓰는 템플릿 계약이라 문자열 변환은 여기서 정리
        String startedAt = (r.startedAt() != null) ? r.startedAt().toString() : "-";

        return new DashboardBatchAttemptVM(
                r.attemptId(),
                r.targetYyyymm(),
                r.executionStatus(),
                r.executionType(),
                startedAt,
                r.durationMs(),
                r.successCount(),
                r.failCount()
        );
    }

    public record DashboardView(
            int billingYyyymm,
            long billingTargetCount,
            long batchOkCount,
            long batchFailCount,
            long sendingTargetCount,
            long sendingSentCount,
            long sendingFailedCount,
            List<DashboardBatchAttemptVM> batchRecentAttempts,
            DashboardBatchAttemptVM batchLatestAttempt,
            List<RecentSendingHistoryVM> recentSendingHistory
    ) {}
}
