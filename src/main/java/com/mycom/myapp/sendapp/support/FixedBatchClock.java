package com.mycom.myapp.sendapp.support;

import lombok.NonNull;

import java.time.LocalDateTime;

/**
 * 테스트 시 임의의 시간을 설정하기 위해 사용되는 모듈
 */
public class FixedBatchClock implements BatchClock{
    private LocalDateTime fixed;
    public FixedBatchClock(@NonNull LocalDateTime fixed) {
        this.fixed = fixed;
    }
    @Override
    public LocalDateTime now() {
        return fixed;
    }
    public void setFixed(@NonNull LocalDateTime fixed) {
        this.fixed = fixed;
    }
}
