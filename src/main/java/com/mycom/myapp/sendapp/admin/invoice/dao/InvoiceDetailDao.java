package com.mycom.myapp.sendapp.admin.invoice.dao;

import com.mycom.myapp.sendapp.admin.invoice.dto.InvoiceDetailRowRawDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InvoiceDetailDao {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceDetailDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** ✅ Service에서 사용하는 표준 메서드명 */
    public List<InvoiceDetailRowRawDTO> listByInvoice(long invoiceId) {
        return findByInvoiceId(invoiceId);
    }

    /**
     * invoice_id 기준 상세 라인 조회
     *
     * Table: monthly_invoice_detail
     */
    public List<InvoiceDetailRowRawDTO> findByInvoiceId(long invoiceId) {
        String sql = """
            SELECT
              d.detail_id,
              d.invoice_id,
              d.invoice_category_id,
              ic.name AS invoice_category_name,
              d.billing_history_id,
              d.service_name,
              d.origin_amount,
              d.discount_amount,
              d.total_amount,
              d.usage_start_date,
              d.usage_end_date,
              d.created_at,
              d.expired_at
            FROM monthly_invoice_detail d
            JOIN invoice_category ic ON d.invoice_category_id = ic.invoice_category_id
            WHERE d.invoice_id = ?
            ORDER BY d.detail_id ASC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date usageStart = rs.getDate("usage_start_date");
            Date usageEnd = rs.getDate("usage_end_date");
            Timestamp created = rs.getTimestamp("created_at");
            Date expired = rs.getDate("expired_at");

            LocalDate usageStartDate = usageStart == null ? null : usageStart.toLocalDate();
            LocalDate usageEndDate = usageEnd == null ? null : usageEnd.toLocalDate();
            LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();
            LocalDate expiredAt = expired == null ? null : expired.toLocalDate();

            return new InvoiceDetailRowRawDTO(
                    rs.getLong("detail_id"),
                    rs.getLong("invoice_id"),
                    rs.getInt("invoice_category_id"),
                    rs.getString("invoice_category_name"),
                    rs.getLong("billing_history_id"),
                    rs.getString("service_name"),
                    rs.getLong("origin_amount"),
                    rs.getLong("discount_amount"),
                    rs.getLong("total_amount"),
                    usageStartDate,
                    usageEndDate,
                    createdAt,
                    expiredAt
            );
        }, invoiceId);
    }
}
