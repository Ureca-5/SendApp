package com.mycom.myapp.sendapp.delivery.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryRetryDto;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;
import com.mycom.myapp.sendapp.delivery.service.DeliveryLoaderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliverySyncWorker {

    private final DeliveryStatusRepository statusRepository;
    private final DeliveryLoaderService loaderService;
    
    @Scheduled(cron = "0 0/30 * * * *") // 30분 주기
    public void syncLostDeliveries() {
        // 1. 1시간 전을 기준으로 그보다 오래된 READY/PROCESSING 데이터 조회
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        
        log.info("🔍 [Sync] 유실 데이터 스캔 시작 (기준: 1시간 전)");
        
        // 상세 조인 정보를 포함한 DTO 리스트 가져오기
        List<DeliveryRetryDto> lostTargets = statusRepository.findZombieTargets(threshold);

        if (lostTargets.isEmpty()) {
            log.info("✅ [Sync] 유실된 데이터가 없습니다.");
            return;
        }

        log.warn("🧟 [Sync] 유실 의심 데이터 {}건 발견. Redis 재적재를 시도합니다.", lostTargets.size());

        // 2. Redis 재적재 호출
        loaderService.rePushToRedis(lostTargets);
    }
}