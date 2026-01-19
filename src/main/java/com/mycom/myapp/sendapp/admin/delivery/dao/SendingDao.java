package com.mycom.myapp.sendapp.admin.delivery.dao;

import com.mycom.myapp.sendapp.admin.delivery.dto.SendingHistoryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusRowDTO;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SendingDao {

    private final JdbcTemplate jdbcTemplate;
    private final ContactProtector protector;

    public SendingDao(JdbcTemplate jdbcTemplate, ContactProtector protector) {
        this.jdbcTemplate = jdbcTemplate;
        this.protector = protector;
    }

    public int count(Integer billingYyyymm, String status, String channel) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM delivery_status ds
            JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, status, channel);

        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return cnt == null ? 0 : cnt;
    }

    public List<SendingStatusRowDTO> list(Integer billingYyyymm, String status, String channel, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              ds.invoice_id,
              mi.billing_yyyymm,
              mi.users_id,
              u.name AS user_name,
              u.phone AS phone_enc,
              ds.status,
              ds.delivery_channel,
              ds.retry_count,
              ds.last_attempt_at,
              ds.created_at
            FROM delivery_status ds
            JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, status, channel);

        sql.append(" ORDER BY ds.created_at DESC, ds.invoice_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Timestamp last = rs.getTimestamp("last_attempt_at");
            Timestamp created = rs.getTimestamp("created_at");
            LocalDateTime lastAt = last == null ? null : last.toLocalDateTime();
            LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();

            String nameMasked = protector.maskedName(rs.getString("user_name"));
            String phoneMasked;
			try {
				phoneMasked = protector.maskedPhone(EncryptedString.of(rs.getString("phone_enc")));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				phoneMasked = null;
			}

            return new SendingStatusRowDTO(
                    rs.getLong("invoice_id"),
                    rs.getInt("billing_yyyymm"),
                    rs.getLong("users_id"),
                    nameMasked,
                    phoneMasked,
                    rs.getString("status"),
                    rs.getString("delivery_channel"),
                    rs.getInt("retry_count"),
                    lastAt,
                    createdAt
            );
        }, args.toArray());
    }

    public List<SendingHistoryRowDTO> history(long invoiceId) {
        String sql = """
            SELECT
              delivery_history_id,
              invoice_id,
              attempt_no,
              delivery_channel,
              receiver_info,
              status,
              error_message,
              requested_at,
              sent_at
            FROM delivery_history
            WHERE invoice_id = ?
            ORDER BY attempt_no ASC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            LocalDateTime requestedAt = toLdt(rs.getTimestamp("requested_at"));
            LocalDateTime sentAt = toLdt(rs.getTimestamp("sent_at"));

            return new SendingHistoryRowDTO(
                    rs.getLong("delivery_history_id"),
                    rs.getLong("invoice_id"),
                    rs.getInt("attempt_no"),
                    rs.getString("delivery_channel"),
                    rs.getString("receiver_info"),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    requestedAt,
                    sentAt
            );
        }, invoiceId);
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static void applyWhere(StringBuilder sql, List<Object> args, Integer billingYyyymm, String status, String channel) {
        if (billingYyyymm != null) {
            sql.append(" AND mi.billing_yyyymm = ? ");
            args.add(billingYyyymm);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND ds.status = ? ");
            args.add(status.trim());
        }
        if (channel != null && !channel.isBlank()) {
            sql.append(" AND ds.delivery_channel = ? ");
            args.add(channel.trim());
        }
    }
}
