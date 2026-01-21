package com.mycom.myapp.sendapp.batch.support;

import com.sun.management.OperatingSystemMXBean;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * JVM 자원 사용률 모니터링.
 * - 주기: 3초
 * - CPU: 프로세스 기준 사용률 > 85%
 * - Heap: 사용률 > 90%
 * - 연속 경고 억제: 30초 이내 중복 경고 무시
 */
@Slf4j
@Component
public class ResourceUsageMonitor {
    private static final double CPU_THRESHOLD = 0.85;
    private static final double HEAP_THRESHOLD = 0.90;
    private static final long SUPPRESS_MILLIS = 30_000L;

    private final OperatingSystemMXBean osBean =
            ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    private volatile long lastWarnAt;

    @PostConstruct
    public void init() {
        lastWarnAt = 0L;
    }

    @Scheduled(fixedDelay = 3000)
    public void check() {
        double cpuLoad = osBean.getProcessCpuLoad(); // 0.0~1.0, 미지원 시 음수
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max = heap.getMax() > 0 ? heap.getMax() : heap.getCommitted();
        double heapRatio = max > 0 ? (double) used / max : 0.0;

        boolean cpuHigh = cpuLoad >= 0 && cpuLoad > CPU_THRESHOLD;
        boolean heapHigh = heapRatio > HEAP_THRESHOLD;

        if (cpuHigh || heapHigh) {
            long now = System.currentTimeMillis();
            if (now - lastWarnAt < SUPPRESS_MILLIS) {
                return; // 최근 30초 내 경고가 있었다면 중복 로그 억제
            }
            lastWarnAt = now;

            log.warn("Resource usage high: cpu={}%, heap={}MB/{}MB ({}%)",
                    Math.round(cpuLoad * 100),
                    toMb(used),
                    toMb(max),
                    String.format("%.1f", heapRatio * 100)
            );
        }
    }

    private long toMb(long bytes) {
        return bytes / (1024 * 1024);
    }
}
