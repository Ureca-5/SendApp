package com.mycom.myapp.sendapp.batch.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 운영환경용 기본 구현체: 시스템 시각 사용.
 */
@Component
public class SystemBatchClock implements BatchClock {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
