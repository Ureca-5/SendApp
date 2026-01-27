package com.mycom.myapp.sendapp.admin.delivery.dao;

import com.mycom.myapp.sendapp.admin.delivery.dto.DeliverySummaryRowDTO;
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

    /** Status 탭: 건수 */
    public int count(Integer billingYyyymm, String status, String deliveryChannel, Long usersId, Long invoiceId) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM delivery_status ds
            LEFT JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            LEFT JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, status, deliveryChannel, usersId, invoiceId);

        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return cnt == null ? 0 : cnt;
    }

    /** Status 탭: 목록 */
    public List<SendingStatusRowDTO> list(Integer billingYyyymm, String status, String deliveryChannel,
                                          Long usersId, Long invoiceId, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              ds.invoice_id,
              mi.billing_yyyymm,
              mi.users_id,
              mi.total_amount,
              u.name AS user_name,
              u.phone AS phone_enc,
              u.email AS email_enc,

              ds.status,
              ds.delivery_channel,
              ds.retry_count,
              ds.last_attempt_at,
              ds.created_at,

              dh_last.receiver_info AS last_receiver_info,
              dh_last.error_message AS last_error_message

            FROM delivery_status ds
            LEFT JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            LEFT JOIN users u ON mi.users_id = u.users_id

            LEFT JOIN (
                SELECT h1.invoice_id, h1.receiver_info, h1.error_message
                FROM delivery_history h1
                JOIN (
                    SELECT invoice_id, MAX(attempt_no) AS max_attempt
                    FROM delivery_history
                    GROUP BY invoice_id
                ) h2 ON h1.invoice_id = h2.invoice_id AND h1.attempt_no = h2.max_attempt
            ) dh_last ON ds.invoice_id = dh_last.invoice_id

            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, status, deliveryChannel, usersId, invoiceId);

        sql.append(" ORDER BY ds.created_at DESC, ds.invoice_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            LocalDateTime lastAt = toLdt(rs.getTimestamp("last_attempt_at"));
            LocalDateTime createdAt = toLdt(rs.getTimestamp("created_at"));

            Integer bym = (Integer) rs.getObject("billing_yyyymm");
            Long uid = (Long) rs.getObject("users_id");

            // 이름 마스킹 //USER쪽 조회없는 가상 USERNAME
            String userName = protector.maskedName(rs.getString("user_name"));

            String channelVal = rs.getString("delivery_channel"); // snapshot 기준
            String receiverMasked = maskReceiver(
                    channelVal,
                    rs.getString("last_receiver_info"),   // 우선: 실제 발송 대상(History 최신)
                    rs.getString("email_enc"),            // fallback: users.email
                    rs.getString("phone_enc")             // fallback: users.phone
            );

            Long totalAmount = null;
            try {
                Object amtObj = rs.getObject("total_amount");
                if (amtObj != null) {
                    totalAmount = ((Number) amtObj).longValue();
                }
            } catch (Exception ignore) {
                totalAmount = null;
            }

            return new SendingStatusRowDTO(
                    rs.getLong("invoice_id"),
                    bym,
                    uid,
                    userName,
                    receiverMasked,
                    channelVal,
                    rs.getString("status"),
                    rs.getInt("retry_count"),
                    totalAmount,
                    lastAt,
                    createdAt
            );
        }, args.toArray());
    }

    /** History 탭: invoice_id 기준 이력 */
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
            String channel = rs.getString("delivery_channel");
            String receiverInfo = rs.getString("receiver_info");
            String receiverMasked = maskReceiver(channel, receiverInfo, null, null);

            return new SendingHistoryRowDTO(
                    rs.getLong("delivery_history_id"),
                    rs.getLong("invoice_id"),
                    rs.getInt("attempt_no"),
                    channel,
                    receiverMasked,
                    rs.getString("status"),
                    rs.getString("error_message"),
                    toLdt(rs.getTimestamp("requested_at")),
                    toLdt(rs.getTimestamp("sent_at"))
            );
        }, invoiceId);
    }

    /** Summary 탭: 월+채널 집계 */
    public List<DeliverySummaryRowDTO> summaries(Integer billingYyyymm) {
        // 테이블/컬럼명이 다르면 여기만 바꾸면 됨.
        StringBuilder sql = new StringBuilder("""
            SELECT
              billing_yyyymm,
              delivery_channel,
              total_attempt_count,
              success_count,
              fail_count,
              success_rate,
              updated_at
            FROM delivery_summary
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        if (billingYyyymm != null) {
            sql.append(" AND billing_yyyymm = ? ");
            args.add(billingYyyymm);
        }
        sql.append(" ORDER BY delivery_channel ASC ");

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) ->
                new DeliverySummaryRowDTO(
                        rs.getInt("billing_yyyymm"),
                        rs.getString("delivery_channel"),
                        rs.getLong("total_attempt_count"),
                        rs.getLong("success_count"),
                        rs.getLong("fail_count"),
                        rs.getInt("success_rate"),
                        toLdt(rs.getTimestamp("updated_at"))
                )
        );
    }

    private String maskReceiver(String channel, String receiverInfoEnc, String emailEnc, String phoneEnc) {
        try {
            // 우선: history.receiver_info (실제 발송 대상)
            if (receiverInfoEnc != null && receiverInfoEnc.startsWith("v1:")) {
                if ("EMAIL".equalsIgnoreCase(channel)) {
                    return protector.maskedEmail(EncryptedString.of(receiverInfoEnc));
                }
                return protector.maskedPhone(EncryptedString.of(receiverInfoEnc));
            }

            // fallback: users.email/users.phone
            if ("EMAIL".equalsIgnoreCase(channel) && emailEnc != null) {
                return protector.maskedEmail(EncryptedString.of(emailEnc));
            }
            if (!"EMAIL".equalsIgnoreCase(channel) && phoneEnc != null) {
                return protector.maskedPhone(EncryptedString.of(phoneEnc));
            }

            // ADMIN_REQUEST 같은 값은 그대로(민감정보가 아니어야 함)
            if (receiverInfoEnc != null && !receiverInfoEnc.isBlank()) {
                return receiverInfoEnc;
            }

            return "-";
        } catch (Exception e) {
            return "(decrypt-failed)";
        }
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static void applyWhere(StringBuilder sql, List<Object> args,
                                   Integer billingYyyymm, String status, String deliveryChannel,
                                   Long usersId, Long invoiceId) {
        // billingYyyymm/usersId는 monthly_invoice가 있어야 의미가 있으므로 LEFT JOIN 상태에서 필터를 걸면 자동으로 NULL row는 제외됨.
        if (billingYyyymm != null) {
            sql.append(" AND mi.billing_yyyymm = ? ");
            args.add(billingYyyymm);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND ds.status = ? ");
            args.add(status.trim());
        }
        if (deliveryChannel != null && !deliveryChannel.isBlank()) {
            sql.append(" AND ds.delivery_channel = ? ");
            args.add(deliveryChannel.trim());
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
