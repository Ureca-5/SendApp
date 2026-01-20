package com.mycom.myapp.sendapp.batch.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 단일 서버(락 테이블 없음) 기준의 BatchStartGuard 구현체.
 *
 * 핵심:
 * - assertStartable(): 해당 월에 STARTED/COMPLETED 존재 여부 확인(필요 시 FOR UPDATE로 갭락 유도 가능)
 * - createStartedAttempt(): STARTED attempt insert 후 생성된 PK 반환
 */
@Component
@RequiredArgsConstructor
public class AttemptOnlyBatchStartGuard implements BatchStartGuard {
    private final JdbcTemplate jdbcTemplate;

    private static final String TARGET_COUNT_SQL = """
        SELECT COUNT(*)
        FROM users u
        WHERE NOT EXISTS (
            SELECT 1
            FROM monthly_invoice mi
            WHERE mi.users_id = u.users_id
              AND mi.billing_yyyymm = ?
        )
        AND (
            EXISTS (
                SELECT 1
                FROM subscribe_billing_history sbh
                WHERE sbh.users_id = u.users_id
                  AND sbh.billing_yyyymm = ?
            )
            OR EXISTS (
                SELECT 1
                FROM micro_payment_billing_history mph
                WHERE mph.users_id = u.users_id
                  AND mph.billing_yyyymm = ?
            )
        )
        """;

    /**
     * Step0(Tasklet)가 이미 트랜잭션을 잡고 실행되므로, Guard는 "현재 트랜잭션 안에서만" 수행되도록 강제합니다.
     *
     * 개발 근거:
     * - Tasklet step은 PlatformTransactionManager로 감싼 하나의 트랜잭션에서 실행되므로
     *   검사/insert가 분리되어 커밋되는 실수를 막기 좋습니다.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertStartable(int targetYyyymm) {
        // 단일 서버라도 "스케줄러 + 수동 실행"이 겹칠 수 있으므로 DB 기준으로 방어합니다.
        // (향후 멀티 서버에선 LockTable Guard로 교체)
        //
        // FOR UPDATE는 "같은 월에 대한 동시 insert"를 더 강하게 막고 싶을 때 사용합니다.
        // InnoDB에서 (target_yyyymm, execution_status) 인덱스가 있으면,
        // 조건에 해당 row가 없더라도 gap lock으로 동시 insert 경쟁을 줄이는 데 도움됩니다.
        //
        // 지금은 단일 서버이므로 필수는 아니지만, 실수를 줄이는 방향으로 넣어둡니다.
        String sql = """
            SELECT attempt_id
            FROM monthly_invoice_batch_attempt
            WHERE target_yyyymm = ?
              AND execution_status IN ('STARTED', 'COMPLETED')
            LIMIT 1
            FOR UPDATE
            """;

        Long existing = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null, targetYyyymm);

        if (existing != null) {
            throw new IllegalStateException(
                    "이미 해당 월의 정산 배치가 시작되었거나 완료되었습니다. targetYyyymm=" + targetYyyymm
                            + ", existingAttemptId=" + existing
            );
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long createStartedAttempt(int targetYyyymm, LocalDateTime startedAt, String hostName) {
        // NOTE:
        // 현재 DDL에는 attempt_id가 AUTO_INCREMENT로 명시되어 있지 않습니다.
        // 이 코드는 "attempt_id가 DB에서 자동 생성된다"는 전제(일반적인 운영 방식)로 구현했습니다.
        // 만약 AUTO_INCREMENT가 아니라면:
        // - 시퀀스 테이블/UUID/애플리케이션 ID 발급 전략이 별도로 필요합니다.
        //
        // target_count(정산 대상 회원 수)는 지금 단계에서는 0으로 넣고,
        // Step0에서 "정산 대상 회원 수 count" 로직이 확정되면 채우는 것으로 진행합니다.

        // 1) 정산 대상 회원 수 카운트 (Reader 조건과 동일해야 함)
        long targetCount = countTargetUsers(targetYyyymm);

        String insertSql = """
            INSERT INTO monthly_invoice_batch_attempt
                (target_yyyymm, execution_status, execution_type, started_at, ended_at, duration_ms,
                 success_count, fail_count, host_name, target_count)
            VALUES
                (?, 'STARTED', 'SCHEDULED', ?, NULL, NULL,
                 0, 0, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            int updated = jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, targetYyyymm);
                ps.setTimestamp(2, Timestamp.valueOf(startedAt));
                ps.setString(3, hostName);
                ps.setLong(4, targetCount);
                return ps;
            }, keyHolder);

            if (updated != 1) {
                throw new IllegalStateException("attempt insert 결과가 1건이 아닙니다. updated=" + updated);
            }

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException(
                        "attempt_id를 생성키로 반환받지 못했습니다. " +
                                "attempt_id가 AUTO_INCREMENT인지 확인해 주세요."
                );
            }
            return key.longValue();

        } catch (DataAccessException e) {
            // 단일 서버라도, 운영 중 수동 실행/스케줄러 중복 등으로 경쟁이 발생할 수 있습니다.
            // 여기서 예외를 래핑하여 호출자가 원인 파악하기 쉽게 합니다.
            throw new IllegalStateException("STARTED attempt 생성에 실패했습니다. targetYyyymm=" + targetYyyymm, e);
        }
    }

    /**
     * [절차]
     * 1) users u를 기준으로, monthly_invoice에 해당 월 헤더가 "없는" 유저만 필터
     * 2) subscribe_billing_history / micro_payment_billing_history 중
     *    해당 월 원천 데이터가 "하나라도 존재"하는 유저만 필터
     * 3) 위 조건을 만족하는 유저 수 COUNT(*)
     */
    private long countTargetUsers(Integer yyyymm) {
        // 바인딩은 총 3번(각 서브쿼리 billing_yyyymm)
        Long count = jdbcTemplate.queryForObject(
                TARGET_COUNT_SQL,
                Long.class,
                yyyymm, yyyymm, yyyymm
        );
        return (count == null) ? 0L : count;
    }
}
