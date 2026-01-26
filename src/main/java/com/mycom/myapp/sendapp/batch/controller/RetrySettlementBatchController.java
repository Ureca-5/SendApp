package com.mycom.myapp.sendapp.batch.controller;

import com.mycom.myapp.sendapp.batch.service.BatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 실패 재시도 배치 트리거용 API
 */
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class RetrySettlementBatchController {
    private final BatchService batchService;

    @PostMapping("/settlement/retry")
    public ResponseEntity<String> retrySettlement() {
        JobExecution execution = batchService.runRetrySettlementBatch();
        return ResponseEntity.ok("retry batch started. executionId=" + execution.getId());
    }

    @PostMapping("/settlement/resume")
    public ResponseEntity<String> resumeStalledSettlement() {
        JobExecution execution = batchService.resumeStalledSettlementBatch();
        return ResponseEntity.ok("resume batch started. executionId=" + execution.getId());
    }
}
