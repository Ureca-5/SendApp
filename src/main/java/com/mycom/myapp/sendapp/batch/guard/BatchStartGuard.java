package com.mycom.myapp.sendapp.batch.guard;

import java.time.LocalDateTime;

/**
 * 배치 시작 가능 여부 검증 + 시작 attempt 생성 책임.
 *
 * 단일 서버 버전에서는 attempt 테이블만 사용합니다.
 * (나중에 멀티 서버로 확장하면 LockTable 기반 Guard 구현체로 교체 가능)
 */
public interface BatchStartGuard {
    /**
     * 해당 월(targetYyyymm)에 대해 이미 STARTED/COMPLETED attempt가 있으면 시작 불가로 예외를 던집니다.
     */
    void assertStartable(int targetYyyymm);

    /**
     * STARTED attempt 레코드를 생성하고 attempt_id(PK)를 반환합니다.
     */
    long createStartedAttempt(int targetYyyymm, LocalDateTime startedAt, String hostName);
}
