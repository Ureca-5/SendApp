package com.mycom.myapp.sendapp.batch.support;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 청크 내 성공 헤더 목록을 일시 보관하기 위한 버퍼.
 * ExecutionContext 대신 메모리 버퍼를 사용해 직렬화/메타데이터 크기 문제를 방지한다.
 */
@Component
public class ChunkHeaderBuffer {
    private final ConcurrentHashMap<Long, List<MonthlyInvoiceRowDto>> buffer = new ConcurrentHashMap<>();

    public void put(Long stepExecutionId, List<MonthlyInvoiceRowDto> headers) {
        if (stepExecutionId == null || headers == null) return;
        buffer.put(stepExecutionId, headers);
    }

    /**
     * 데이터를 가져오며 제거한다.
     */
    public List<MonthlyInvoiceRowDto> poll(Long stepExecutionId) {
        if (stepExecutionId == null) return null;
        return buffer.remove(stepExecutionId);
    }
}
