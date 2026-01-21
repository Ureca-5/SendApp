package com.mycom.myapp.sendapp.admin.delivery.dao;

import com.mycom.myapp.sendapp.admin.delivery.dto.SendingHistoryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingStatusSummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingChannelStatusSummaryRowDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingKpiDTO;
import com.mycom.myapp.sendapp.admin.delivery.dto.SendingRecentHistoryRowDTO;
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

    public int count(Integer billingYyyymm, String status, String channel, Long usersId, Long invoiceId) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM delivery_status ds
            JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, status, channel, usersId, invoiceId);

        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return cnt == null ? 0 : cnt;
    }

    public List<SendingStatusRowDTO> list(Integer billingYyyymm, String status, String channel, Long usersId, Long invoiceId, int size, int offset) {
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
        applyWhere(sql, args, billingYyyymm, status, channel, usersId, invoiceId);

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
                phoneMasked = "(decrypt-failed)";
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

    
public SendingKpiDTO kpi(Integer billingYyyymm) {
    StringBuilder sql = new StringBuilder("""
        SELECT
          COUNT(*) AS target_cnt,
          SUM(CASE WHEN ds.status = 'READY' THEN 1 ELSE 0 END) AS ready_cnt,
          SUM(CASE WHEN ds.status = 'SENT' THEN 1 ELSE 0 END) AS sent_cnt,
          SUM(CASE WHEN ds.status = 'FAILED' THEN 1 ELSE 0 END) AS failed_cnt
        FROM delivery_status ds
        JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
        WHERE 1=1
    """);
    List<Object> args = new ArrayList<>();
    if (billingYyyymm != null) {
        sql.append(" AND mi.billing_yyyymm = ? ");
        args.add(billingYyyymm);
    }

    return jdbcTemplate.queryForObject(sql.toString(), args.toArray(), (rs, rowNum) ->
            new SendingKpiDTO(
                    rs.getLong("target_cnt"),
                    rs.getLong("ready_cnt"),
                    rs.getLong("sent_cnt"),
                    rs.getLong("failed_cnt")
            )
    );
}

public List<SendingStatusSummaryRowDTO> statusSummary(Integer billingYyyymm) {
    StringBuilder sql = new StringBuilder("""
        SELECT ds.status, COUNT(*) AS cnt
        FROM delivery_status ds
        JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
        WHERE 1=1
    """);
    List<Object> args = new ArrayList<>();
    if (billingYyyymm != null) {
        sql.append(" AND mi.billing_yyyymm = ? ");
        args.add(billingYyyymm);
    }
    sql.append(" GROUP BY ds.status ORDER BY cnt DESC ");
    return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) ->
            new SendingStatusSummaryRowDTO(rs.getString("status"), rs.getLong("cnt"))
    );
}

public List<SendingChannelStatusSummaryRowDTO> channelStatusSummary(Integer billingYyyymm) {
    StringBuilder sql = new StringBuilder("""
        SELECT ds.delivery_channel AS channel, ds.status, COUNT(*) AS cnt
        FROM delivery_status ds
        JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
        WHERE 1=1
    """);
    List<Object> args = new ArrayList<>();
    if (billingYyyymm != null) {
        sql.append(" AND mi.billing_yyyymm = ? ");
        args.add(billingYyyymm);
    }
    sql.append(" GROUP BY ds.delivery_channel, ds.status ORDER BY channel, cnt DESC ");
    return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) ->
            new SendingChannelStatusSummaryRowDTO(rs.getString("channel"), rs.getString("status"), rs.getLong("cnt"))
    );
}

public List<SendingStatusRowDTO> listByUser(Integer billingYyyymm, long usersId) {
    return list(billingYyyymm, null, null, usersId, null, 200, 0);
}

public List<SendingRecentHistoryRowDTO> recentHistory(Integer billingYyyymm, int limit) {
    StringBuilder sql = new StringBuilder("""
        SELECT
          dh.invoice_id,
          mi.users_id,
          dh.delivery_channel,
          dh.status,
          dh.error_message,
          dh.requested_at
        FROM delivery_history dh
        JOIN monthly_invoice mi ON dh.invoice_id = mi.invoice_id
        WHERE 1=1
    """);
    List<Object> args = new ArrayList<>();
    if (billingYyyymm != null) {
        sql.append(" AND mi.billing_yyyymm = ? ");
        args.add(billingYyyymm);
    }
    sql.append(" ORDER BY dh.requested_at DESC, dh.delivery_history_id DESC LIMIT ? ");
    args.add(Math.max(limit, 1));

    return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) ->
            new SendingRecentHistoryRowDTO(
                    rs.getLong("invoice_id"),
                    rs.getLong("users_id"),
                    rs.getString("delivery_channel"),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    toLdt(rs.getTimestamp("requested_at"))
            )
    );
}

/**
 * 운영 안전을 위해 "실제 발송"이 아니라 FAILED -> READY 전환만 한다.
 * resendChannel로 delivery_channel을 바꾸고, delivery_history에 ADMIN_REQUEST 기록을 추가한다.
 */
public int requestResendUser(Integer billingYyyymm, long usersId, String failedChannel, String resendChannel) {
    int updated = updateFailedToReady(billingYyyymm, usersId, null, failedChannel, resendChannel);
    if (updated > 0) {
        insertAdminReadyHistory(billingYyyymm, usersId, null, resendChannel);
    }
    return updated;
}

public int requestResendBulk(Integer billingYyyymm, String failedChannel, String resendChannel) {
    int updated = updateFailedToReady(billingYyyymm, null, null, failedChannel, resendChannel);
    if (updated > 0) {
        insertAdminReadyHistory(billingYyyymm, null, null, resendChannel);
    }
    return updated;
}

private int updateFailedToReady(Integer billingYyyymm, Long usersId, Long invoiceId, String failedChannel, String resendChannel) {
    StringBuilder sql = new StringBuilder("""
        UPDATE delivery_status ds
        JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
        SET ds.status = 'READY',
            ds.delivery_channel = ?,
            ds.last_attempt_at = NOW()
        WHERE ds.status = 'FAILED'
    """);
    List<Object> args = new ArrayList<>();
    args.add(resendChannel);

    if (billingYyyymm != null) { sql.append(" AND mi.billing_yyyymm = ? "); args.add(billingYyyymm); }
    if (usersId != null) { sql.append(" AND mi.users_id = ? "); args.add(usersId); }
    if (invoiceId != null) { sql.append(" AND ds.invoice_id = ? "); args.add(invoiceId); }
    if (failedChannel != null && !failedChannel.isBlank()) { sql.append(" AND ds.delivery_channel = ? "); args.add(failedChannel.trim()); }

    return jdbcTemplate.update(sql.toString(), args.toArray());
}

private int insertAdminReadyHistory(Integer billingYyyymm, Long usersId, Long invoiceId, String resendChannel) {
    StringBuilder sql = new StringBuilder("""
        INSERT INTO delivery_history (
          invoice_id,
          attempt_no,
          delivery_channel,
          receiver_info,
          status,
          error_message,
          requested_at,
          sent_at
        )
        SELECT
          ds.invoice_id,
          COALESCE((
            SELECT MAX(dh2.attempt_no) + 1
            FROM delivery_history dh2
            WHERE dh2.invoice_id = ds.invoice_id
          ), 1) AS attempt_no,
          ? AS delivery_channel,
          'ADMIN_REQUEST' AS receiver_info,
          'READY' AS status,
          NULL AS error_message,
          NOW() AS requested_at,
          NULL AS sent_at
        FROM delivery_status ds
        JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
        WHERE ds.status = 'READY'
    """);
    List<Object> args = new ArrayList<>();
    args.add(resendChannel);

    if (billingYyyymm != null) { sql.append(" AND mi.billing_yyyymm = ? "); args.add(billingYyyymm); }
    if (usersId != null) { sql.append(" AND mi.users_id = ? "); args.add(usersId); }
    if (invoiceId != null) { sql.append(" AND ds.invoice_id = ? "); args.add(invoiceId); }

    // READY로 바뀐 row 중 resendChannel인 것만 history 기록 (중복 최소화)
    sql.append(" AND ds.delivery_channel = ? ");
    args.add(resendChannel);

    return jdbcTemplate.update(sql.toString(), args.toArray());
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

    private static void applyWhere(StringBuilder sql, List<Object> args, Integer billingYyyymm, String status, String channel, Long usersId, Long invoiceId) {
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
        if (usersId != null) {
            sql.append(" AND mi.users_id = ? ");
            args.add(usersId);
        }
        if (invoiceId != null) {
            sql.append(" AND ds.invoice_id = ? ");
            args.add(invoiceId);
        }
    }
}
