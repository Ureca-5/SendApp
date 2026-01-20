package com.mycom.myapp.sendapp.admin.invoice.dao;

import com.mycom.myapp.sendapp.admin.invoice.dto.InvoiceDetailRowRawDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InvoiceDetailDao {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceDetailDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InvoiceDetailRowRawDTO> findByInvoiceId(long invoiceId) {
        String sql = """
            SELECT
              detail_id,
              invoice_id,
              invoice_category_id,
              service_name,
              origin_amount,
              discount_amount,
              total_amount,
              usage_start_date,
              usage_end_date
            FROM invoice_detail
            WHERE invoice_id = ?
            ORDER BY invoice_category_id, usage_start_date, detail_id
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Timestamp s = rs.getTimestamp("usage_start_date");
            Timestamp e = rs.getTimestamp("usage_end_date");
            LocalDateTime start = s == null ? null : s.toLocalDateTime();
            LocalDateTime end = e == null ? null : e.toLocalDateTime();

            return new InvoiceDetailRowRawDTO(
                    rs.getLong("detail_id"),
                    rs.getLong("invoice_id"),
                    rs.getInt("invoice_category_id"),
                    rs.getString("service_name"),
                    rs.getLong("origin_amount"),
                    rs.getLong("discount_amount"),
                    rs.getLong("total_amount"),
                    start,
                    end
            );
        }, invoiceId);
    }
}
