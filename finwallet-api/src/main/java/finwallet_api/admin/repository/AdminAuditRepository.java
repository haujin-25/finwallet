package finwallet_api.admin.repository;

import finwallet_api.admin.dto.AdminAuditLog;
import finwallet_api.admin.dto.AdminAuditLog.Action;
import finwallet_api.admin.dto.AdminAuditLog.Outcome;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminAuditRepository {

    private final JdbcTemplate jdbc;

    public AdminAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(long actorId, Action action, long walletId, OffsetDateTime occurredAt,
                       String reason, String previousStatus, String newStatus, Outcome outcome) {
        jdbc.update("""
                INSERT INTO admin_audit_logs (administrator_user_id, action, wallet_id, occurred_at, reason,
                                              previous_status, new_status, outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, actorId, action.name(), walletId, occurredAt, reason, previousStatus, newStatus, outcome.name());
    }

    public List<AdminAuditLog> findPage(long offset, int limit) {
        return jdbc.query("""
                SELECT id, administrator_user_id, action, wallet_id, occurred_at, reason,
                       previous_status, new_status, outcome
                FROM admin_audit_logs
                ORDER BY occurred_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (row, index) -> new AdminAuditLog(row.getLong("id"), row.getLong("administrator_user_id"),
                Action.valueOf(row.getString("action")), row.getLong("wallet_id"),
                row.getObject("occurred_at", OffsetDateTime.class), row.getString("reason"),
                row.getString("previous_status"), row.getString("new_status"), Outcome.valueOf(row.getString("outcome"))),
                limit, offset);
    }
}
