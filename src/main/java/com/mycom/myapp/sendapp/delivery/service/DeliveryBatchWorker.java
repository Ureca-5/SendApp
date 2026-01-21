package com.mycom.myapp.sendapp.delivery.service;

import com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey;
import com.mycom.myapp.sendapp.delivery.dto.ProcessResult;
import com.mycom.myapp.sendapp.delivery.processor.DeliveryProcessor;
import com.mycom.myapp.sendapp.delivery.service.DeliveryPersistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Component
//@RequiredArgsConstructor
public class DeliveryBatchWorker {

    private final StringRedisTemplate redisTemplate;
    private final DeliveryProcessor deliveryProcessor;
    private final DeliveryPersistService deliveryPersistService;
    private final Executor deliveryExecutor;

    public DeliveryBatchWorker(
            StringRedisTemplate redisTemplate,
            DeliveryProcessor deliveryProcessor,
            DeliveryPersistService deliveryResultService,
            @Qualifier("applicationTaskExecutor") Executor deliveryExecutor // 이 부분이 핵심
    ) {
        this.redisTemplate = redisTemplate;
        this.deliveryProcessor = deliveryProcessor;
        this.deliveryPersistService = deliveryResultService;
        this.deliveryExecutor = deliveryExecutor;
    }
    
    private static final int FETCH_COUNT = 1000;
    private static final int CHUNK_SIZE = 100;

    @Scheduled(fixedDelay = 500)
    public void run() {
        // 1. Redis Stream Read (Type Safety 보완)
        @SuppressWarnings("unchecked")
        List<MapRecord<String, String, String>> records = (List) redisTemplate.opsForStream().read(
            Consumer.from(DeliveryRedisKey.GROUP_NAME, "worker-1"),
            StreamReadOptions.empty().count(FETCH_COUNT).block(Duration.ofMillis(1000)),
            StreamOffset.create(DeliveryRedisKey.WAITING_STREAM, ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) return;

        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 1,000건을 100건씩 수동으로 분할하여 Future 리스트 생성
            List<CompletableFuture<List<ProcessResult>>> futures = new ArrayList<>();
            
            for (int i = 0; i < records.size(); i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, records.size());
                final List<MapRecord<String, String, String>> chunk = records.subList(i, end);

                // 익명 클래스로 작업 정의 (람다 X)
                CompletableFuture<List<ProcessResult>> future = CompletableFuture.supplyAsync(new Supplier<List<ProcessResult>>() {
                    @Override
                    public List<ProcessResult> get() {
                        List<ProcessResult> results = new ArrayList<>();
                        for (MapRecord<String, String, String> record : chunk) {
                            results.add(deliveryProcessor.execute(record.getValue()));
                        }
                        return results;
                    }
                }, deliveryExecutor).exceptionally(new Function<Throwable, List<ProcessResult>>() {
                    @Override
                    public List<ProcessResult> apply(Throwable t) {
                        log.error("⚠️ [Chunk Error] 병렬 처리 중 에러: {}", t.getMessage());
                        return new ArrayList<>();
                    }
                });

                futures.add(future);
            }

            // 3. 결과 집합 및 정제
            List<ProcessResult> allResults = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull) // exceptionally에서 넘어온 null 필터링
                .flatMap(List::stream)
                .collect(Collectors.toList());

            // 4. DB 반영 (Skipped 제외)
            List<ProcessResult> dbTargets = allResults.stream()
                .filter(r -> !r.isSkipped())
                .collect(Collectors.toList());

            if (!dbTargets.isEmpty()) {
                // 트랜잭션 보장 (Update + History Insert)
            	deliveryPersistService.saveBatchResults(dbTargets);
            }

            // 5. ACK 처리: DB 반영이 완벽히 성공한 후에만 수행 (유실 방지)
            RecordId[] ids = records.stream().map(MapRecord::getId).toArray(RecordId[]::new);
            redisTemplate.opsForStream().acknowledge(
                DeliveryRedisKey.WAITING_STREAM, 
                DeliveryRedisKey.GROUP_NAME, 
                ids
            );

            logSummary(allResults, dbTargets.size(), System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            // DB 에러 혹은 기타 장애 발생 시 ACK를 하지 않음으로써 Redis Pending List에 남겨둠 (재처리 보장)
            log.error("🚨 [Critical Error] 배치 처리 중단 (ACK 미수행): {}", e.getMessage(), e);
        }
    }

    private void logSummary(List<ProcessResult> results, int dbCount, long timeMs) {
        long sent = results.stream().filter(r -> "SENT".equals(r.getStatus()) && !r.isSkipped()).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long skipped = results.stream().filter(ProcessResult::isSkipped).count();

        log.info("[Delivery Result] Total: {} | Success: {} | Failed: {} | Skipped: {} | DB_Update: {} | Latency: {}ms",
            results.size(), sent, failed, skipped, dbCount, timeMs);
    }
}