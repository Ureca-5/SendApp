package com.mycom.myapp.sendapp.batch.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.batch.invoice 의 환경변수 값을 주입받아 필드로 저장한 뒤 제공하는 클래스
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.batch.invoice")
public class BatchInvoiceProperties {
    private final int chunkSize;
    private final int microPageSize;
    private final int readerPageSize; // reader에서 한 번에 읽는 레코드 수
    private final int subDetailBatchSize; // 구독 상세 insert 레코드 수 단위
}
