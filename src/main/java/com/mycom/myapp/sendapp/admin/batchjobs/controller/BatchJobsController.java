package com.mycom.myapp.sendapp.admin.batchjobs.controller;

import com.mycom.myapp.sendapp.admin.batchjobs.dao.BatchAttemptDao;
import com.mycom.myapp.sendapp.admin.batchjobs.dao.BatchjobsFailDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class BatchJobsController {

    private final BatchAttemptDao batchAttemptDao;
    private final BatchjobsFailDao batchFailDao;

    public BatchJobsController(BatchAttemptDao batchAttemptDao, BatchjobsFailDao batchFailDao) {
        this.batchAttemptDao = batchAttemptDao;
        this.batchFailDao = batchFailDao;
    }

    @GetMapping("/batch-jobs")
    public String page(
            @RequestParam(name = "yyyymm", required = false) Integer yyyymm,
            @RequestParam(name = "tab", required = false, defaultValue = "attempts") String tab,
            @RequestParam(name = "limit", required = false, defaultValue = "50") Integer limit,
            @RequestParam(name = "attemptId", required = false) Long attemptId,
            Model model
    ) {
        int ym = (yyyymm == null ? nowYyyymm() : yyyymm);

        model.addAttribute("pageTitle", "Batch Jobs");
        model.addAttribute("yyyymm", ym);
        model.addAttribute("tab", normalizeTab(tab));
        model.addAttribute("limit", limit == null ? 50 : limit);
        model.addAttribute("attemptId", attemptId);

        if ("fails".equals(model.getAttribute("tab"))) {
            // fail 테이블은 yyyymm 컬럼이 없으므로 attempt JOIN으로 월 필터링
            model.addAttribute("fails",
                    batchFailDao.listFailsByYyyymm(ym, attemptId, limit));
        } else {
            model.addAttribute("stats", batchAttemptDao.statsByStatus(ym));
            model.addAttribute("rows", batchAttemptDao.listRecentWithLastFail(ym, limit));
        }

        return "admin/batch-jobs";
    }

    private static String normalizeTab(String tab) {
        if (tab == null) return "attempts";
        return "fails".equals(tab) ? "fails" : "attempts";
    }

    private static int nowYyyymm() {
        LocalDate d = LocalDate.now();
        return d.getYear() * 100 + d.getMonthValue();
    }
}
