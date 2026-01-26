package com.mycom.myapp.sendapp.delivery.service;

import com.mycom.myapp.sendapp.delivery.dto.ProcessResult;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryHistoryRepository;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryPersistService {

    private final DeliveryStatusRepository statusRepository;
    private final DeliveryHistoryRepository historyRepository;

    @Transactional
    public void saveBatchResults(List<ProcessResult> results) {
        if (results.isEmpty()) return;
        
        LocalDateTime now = LocalDateTime.now();
        int currentYyyymm = DeliveryLoaderService.currentProcessingYyyymm;
        
        // 1. 상태 업데이트 (SENT/FAILED)
        statusRepository.updateStatusBatch(results, now);
        
        // 2. 이력 적재
        historyRepository.saveHistoryBatch(results, now, currentYyyymm);
    }
}