package com.mycom.myapp.sendapp.invoice.dao;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailDetailRawDTO;
import com.mycom.myapp.sendapp.invoice.dto.InvoiceEmailHeaderRawDTO;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class InvoiceEmailDao {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceEmailHeaderRawDTO loadHeader(long invoiceId) {
        String sql = """
            SELECT
  mi.invoice_id,
  mi.users_id,
  mi.billing_yyyymm,
  mi.total_amount,
  mi.due_date,
  mi.created_at,

  u.name AS user_name,
  u.phone AS user_phone,
  u.payment_method,
  u.payment_info,

  MIN(CASE WHEN mid.invoice_category_id IN (1,2,3) THEN mid.usage_start_date END) AS usage_start_date,
  MAX(CASE WHEN mid.invoice_category_id IN (1,2,3) THEN mid.usage_end_date END)   AS usage_end_date,

  COALESCE(SUM(CASE WHEN mid.invoice_category_id IN (1,3) THEN mid.total_amount ELSE 0 END), 0) AS sum_a_amount,
  COALESCE(SUM(CASE WHEN mid.invoice_category_id = 2      THEN mid.total_amount ELSE 0 END), 0) AS sum_b_amount,
  COALESCE(SUM(CASE WHEN mid.invoice_category_id = 4      THEN mid.total_amount ELSE 0 END), 0) AS sum_c_amount
FROM monthly_invoice mi
JOIN users u ON u.users_id = mi.users_id
LEFT JOIN monthly_invoice_detail mid ON mid.invoice_id = mi.invoice_id   -- ✅ 변경
WHERE mi.invoice_id = ?
GROUP BY
  mi.invoice_id, mi.users_id, mi.billing_yyyymm, mi.total_amount, mi.due_date, mi.created_at,
  u.name, u.phone, u.payment_method, u.payment_info;

            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new InvoiceEmailHeaderRawDTO(
                rs.getLong("invoice_id"),
                rs.getLong("users_id"),
                rs.getInt("billing_yyyymm"),
                rs.getLong("total_amount"),
                rs.getDate("due_date"),
                rs.getTimestamp("created_at"),
                rs.getString("user_name"),
                rs.getString("user_phone"),
                rs.getString("payment_method"),
                rs.getString("payment_info"),
                rs.getDate("usage_start_date"),
                rs.getDate("usage_end_date"),
                rs.getLong("sum_a_amount"),
                rs.getLong("sum_b_amount"),
                rs.getLong("sum_c_amount")
        ), invoiceId);
    }

    public List<InvoiceEmailDetailRawDTO> loadDetails(long invoiceId) {
        String sql = """
            SELECT
              mid.detail_id,
              mid.invoice_category_id,
              mid.service_name,
              mid.origin_amount,
              mid.discount_amount,
              mid.total_amount,
              mid.usage_start_date,
              mid.usage_end_date
            FROM monthly_invoice_detail mid
            WHERE mid.invoice_id = ?
            ORDER BY
              CASE
                WHEN mid.invoice_category_id IN (1,3) THEN 1
                WHEN mid.invoice_category_id = 2      THEN 2
                WHEN mid.invoice_category_id = 4      THEN 3
                ELSE 99
              END,
              mid.detail_id
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new InvoiceEmailDetailRawDTO(
                rs.getLong("detail_id"),
                rs.getInt("invoice_category_id"),
                rs.getString("service_name"),
                rs.getLong("origin_amount"),
                rs.getLong("discount_amount"),
                rs.getLong("total_amount"),
                rs.getDate("usage_start_date"),
                rs.getDate("usage_end_date")
        ), invoiceId);
    }
}
