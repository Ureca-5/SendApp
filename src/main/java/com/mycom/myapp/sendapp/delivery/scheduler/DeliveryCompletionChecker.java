package com.mycom.myapp.sendapp.delivery.scheduler;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.*;

import java.util.List;
import java.util.Map;

import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey;
import com.mycom.myapp.sendapp.delivery.repository.DeliverySummaryRepository;
import com.mycom.myapp.sendapp.delivery.service.DeliveryLoaderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletionChecker {

    private final StringRedisTemplate redisTemplate;
    private final DeliverySummaryRepository summaryRepository;
    private final ThreadPoolTaskExecutor deliveryExecutor; // 사용 중인 쓰레드 풀

    // 1분마다 종료 여부 체크
    @Scheduled(fixedDelay = 60000)
    public void checkCompletion() {
//    	log.info("--- Checker 실행됨 (현재 YYYYMM: {}) ---", DeliveryLoaderService.currentProcessingYyyymm);
    	
        int targetYyyymm = DeliveryLoaderService.currentProcessingYyyymm;
        if (targetYyyymm == 0) return;

        long pendingCount = 0;
        long lagCount = 0;

        try {
            // ClassCastException 방지를 위해 정확한 타입인 XInfoGroups를 사용합니다.
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(DeliveryRedisKey.WAITING_STREAM);

            if (groups != null) {
                // iterator를 통해 순회하며 해당 그룹 정보를 찾습니다.
                for (StreamInfo.XInfoGroup group : groups) {
                    // 이전 대화에서 확인했듯이 메서드 이름은 라이브러리 버전에 따라 다를 수 있습니다.
                    // 보통 groupName() 혹은 getGroupName() 중 하나입니다.
                    if (DeliveryRedisKey.GROUP_NAME.equals(group.groupName())) {
                        pendingCount = group.pendingCount();
                        // lag을 가져오는 메서드가 없다면 0으로 두고 pending만 체크해도 무방합니다.
                        // lagCount = group.unacknowledgedMessages(); // 버전 확인 필요
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Redis Stream 정보를 가져오는 중 오류 발생 (키가 없을 수 있음): {}", e.getMessage());
        }

        Long zsetCount = redisTemplate.opsForZSet().size(DeliveryRedisKey.DELAY_ZSET);
        int activeCount = deliveryExecutor.getActiveCount();

        log.info("[Status] Pending: {}, Lag: {}, ZSet: {}, Active: {}", pendingCount, lagCount, zsetCount, activeCount);

        if (pendingCount == 0 && (zsetCount == null || zsetCount == 0) && activeCount == 0) {
            log.info("[최종 발송 완료] {}월분 통계 적재 시작", targetYyyymm);
            summaryRepository.insertSummary(targetYyyymm);
            DeliveryLoaderService.currentProcessingYyyymm = 0;
        }
    }
    
    
}