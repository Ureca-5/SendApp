package com.mycom.myapp.sendapp.batch.repository.settlement;


import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceBatchFailRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class InvoiceSettlementStatusRepositoryImpl implements InvoiceSettlementStatusRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public int batchInsert(List<MonthlyInvoiceBatchFailRowDto> rows) {
        // [0] 방어 로직: 입력이 비어있으면 DB 접근 자체를 하지 않음
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        // [1] AUTO_INCREMENT인 fail_id는 INSERT 컬럼 목록에서 제외합니다.
        //     (attempt_id, error_code, error_message, created_at, invoice_category_id, billing_history_id)
        final String sql = """
            INSERT INTO monthly_invoice_batch_fail
              (attempt_id, error_code, error_message, created_at, invoice_category_id, billing_history_id)
            VALUES
              (?, ?, ?, ?, ?, ?)
            """;

        // [2] batchSize는 너무 크게 잡지 않고, 안전하게 1000 이하(혹은 500) 정도로 운용하는 편이 일반적입니다.
        //     여기서는 1000으로 두겠습니다.
        final int batchSize = 1000;

        /*
         * [3] 중요: 아래 오버로드는 "여러 배치 묶음"으로 나뉘어 실행될 수 있어서 반환 타입이 int[][] 입니다.
         *     - 바깥 배열: 배치 묶음의 개수
         *     - 안쪽 배열: 해당 묶음에서 각 row의 update count
         *
         *     따라서 `int[] updated` 로 받으면 컴파일 에러가 납니다.
         */
        int[][] updated = jdbcTemplate.batchUpdate(
                sql,
                rows,
                batchSize,
                (ps, row) -> {
                    // [4] wrapper 타입 null-safe 세팅:
                    //     - NOT NULL 컬럼은 null이면 바로 예외로 터지게 두는 편이 디버깅에 유리합니다.
                    //     - nullable 컬럼은 setNull 사용.

                    // 1) attempt_id (NOT NULL)
                    if (row.getAttemptId() == null) {
                        throw new IllegalArgumentException("attemptId is required.");
                    }
                    ps.setLong(1, row.getAttemptId());

                    // 2) error_code (NOT NULL)
                    if (row.getErrorCode() == null) {
                        throw new IllegalArgumentException("errorCode is required.");
                    }
                    ps.setString(2, row.getErrorCode());

                    // 3) error_message (nullable)
                    if (row.getErrorMessage() == null) {
                        ps.setNull(3, Types.LONGVARCHAR);
                    } else {
                        ps.setString(3, row.getErrorMessage());
                    }

                    // 4) created_at (NOT NULL)
                    if (row.getCreatedAt() == null) {
                        throw new IllegalArgumentException("createdAt is required.");
                    }
                    ps.setTimestamp(4, Timestamp.valueOf(row.getCreatedAt()));

                    // 5) invoice_category_id (NOT NULL)
                    if (row.getInvoiceCategoryId() == null) {
                        throw new IllegalArgumentException("invoiceCategoryId is required.");
                    }
                    ps.setInt(5, row.getInvoiceCategoryId());

                    // 6) billing_history_id (NOT NULL)
                    if (row.getBillingHistoryId() == null) {
                        throw new IllegalArgumentException("billingHistoryId is required.");
                    }
                    ps.setLong(6, row.getBillingHistoryId());
                }
        );

        // [5] int[][] -> 총 반영 row 수로 집계
        //     (MySQL은 보통 성공 시 1, 실패/미반영 시 0; 드라이버/옵션에 따라 Statement.SUCCESS_NO_INFO(-2)도 가능)
        int affected = Arrays.stream(updated)
                .flatMapToInt(Arrays::stream)
                .map(v -> v > 0 ? v : 0) // -2 같은 케이스는 0으로 치환
                .sum();

        return affected;
    }
}
