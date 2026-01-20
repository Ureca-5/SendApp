package com.mycom.myapp.sendapp.batch.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 역할: 조건을 만족하는 회원의 usersId 조회 <br>
 * 1. users_id > lastUserId (키셋 페이징) <br>
 * 2. 정산 당월 청구서가 아직 없는 경우 <br>
 * 3. 당월 정산해야할 구독 또는 단건 결제 원천 데이터가 하나라도 있는 경우
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementTargetUserReader implements ItemStreamReader<List<Long>> {
    private static final String CTX_KEY_LAST_USER_ID = "settlementTargetReader.lastUserId";

    private final JdbcTemplate jdbcTemplate;

    /**
     * StepExecutionContext에 저장되는 lastUserId (재시작 지점)
     * - 처음 실행이면 0으로 시작 (users_id는 BIGINT PK라 가정)
     */
    private long lastUserId = 0L;

    /**
     * JobParameter로 주입받는 값
     */
    private int targetYyyymm;
    private int chunkUserSize;

    /**
     * StepConfig에서 setter로 주입하거나, 생성자/팩토리로 주입해도 됩니다.
     */
    public void setTargetYyyymm(int targetYyyymm) {
        this.targetYyyymm = targetYyyymm;
    }

    public void setChunkUserSize(int chunkUserSize) {
        this.chunkUserSize = chunkUserSize;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (executionContext.containsKey(CTX_KEY_LAST_USER_ID)) {
            this.lastUserId = executionContext.getLong(CTX_KEY_LAST_USER_ID);
            log.info("SettlementTargetUserChunkReader restart detected. lastUserId={}", this.lastUserId);
        } else {
            this.lastUserId = 0L;
            log.info("SettlementTargetUserChunkReader start. lastUserId={}", this.lastUserId);
        }
    }

    @Override
    public List<Long> read() {
        // 더 이상 대상이 없으면 null 반환 => Step 종료
        List<Long> userIds = jdbcTemplate.queryForList(
                """
                SELECT u.users_id
                FROM users u
                WHERE u.users_id > ?
                  AND NOT EXISTS (
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
                ORDER BY u.users_id
                LIMIT ?
                """,
                Long.class,
                lastUserId,
                targetYyyymm,
                targetYyyymm,
                targetYyyymm,
                chunkUserSize
        );

        if (userIds == null || userIds.isEmpty()) {
            return null;
        }

        // 키셋 방식: 이번에 읽은 마지막 users_id를 다음 시작점으로 저장
        lastUserId = userIds.get(userIds.size() - 1);

        return userIds;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(CTX_KEY_LAST_USER_ID, this.lastUserId);
    }

    @Override
    public void close() throws ItemStreamException {
        // no-op
    }
}
