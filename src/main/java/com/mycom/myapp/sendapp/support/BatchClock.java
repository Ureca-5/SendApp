package com.mycom.myapp.sendapp.support;

import java.time.LocalDateTime;

/**
 * 배치에서 "현재 시각" 의존성을 분리하기 위한 Clock 추상화.
 *
 * 개발 근거:
 * - Step0 attempt.started_at, Step 종료 시간/소요시간, 락 lease_until 같은 시간을 기록할 때
 *   System clock에 직접 의존하면 테스트가 불안정해집니다.
 * - BatchClock을 주입하면 고정 시각(Fixed)으로 테스트가 가능합니다.
 */
public interface BatchClock {
    LocalDateTime now();
}
