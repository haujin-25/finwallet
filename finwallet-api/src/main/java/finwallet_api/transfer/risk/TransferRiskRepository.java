package finwallet_api.transfer.risk;

import finwallet_api.transfer.entity.Transfer;
import java.math.BigInteger;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class TransferRiskRepository {

    private static final String COMPLETED_CUSTOMER_TRANSFERS = """
            FROM transfers
            WHERE sender_wallet_id = ? AND status = 'COMPLETED' AND reversal_of_transfer_id IS NULL
            """;

    private final JdbcTemplate jdbc;

    public TransferRiskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DailyUsage dailyUsage(long senderWalletId, OffsetDateTime start, OffsetDateTime end) {
        // A joined transaction with a stale snapshot would miss the preceding lock holder's commit.
        if (!Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalStateException("Daily transfer quota requires a READ COMMITTED transaction");
        }
        return jdbc.queryForObject("""
                SELECT coalesce(sum(amount), 0) AS amount, count(*) AS count
                """ + COMPLETED_CUSTOMER_TRANSFERS + "AND created_at >= ? AND created_at < ?",
                (row, index) -> new DailyUsage(row.getBigDecimal("amount").toBigIntegerExact(),
                        row.getLong("count")), senderWalletId, start, end);
    }

    public RecentUsage recentUsage(long senderWalletId, long recipientWalletId,
                                   OffsetDateTime start, OffsetDateTime postingTime) {
        return jdbc.queryForObject("""
                SELECT count(*) AS count, count(DISTINCT recipient_wallet_id) AS recipients,
                       coalesce(bool_or(recipient_wallet_id = ?), false) AS recipient_seen
                """ + COMPLETED_CUSTOMER_TRANSFERS + "AND created_at >= ? AND created_at <= ?",
                (row, index) -> new RecentUsage(row.getLong("count"), row.getLong("recipients"),
                        row.getBoolean("recipient_seen")), recipientWalletId, senderWalletId, start, postingTime);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Transfer transfer, List<TransferRiskRule> rules) {
        for (var rule : rules) {
            jdbc.update("""
                    INSERT INTO transfer_risk_alerts (transfer_id, rule, occurred_at) VALUES (?, ?, ?)
                    """, transfer.getId(), rule.name(), transfer.getCreatedAt());
        }
    }

    public List<Alert> readPage(long offset, int limit) {
        return jdbc.query("""
                SELECT a.id, t.reference, t.sender_wallet_id, a.rule, a.occurred_at
                FROM transfer_risk_alerts a JOIN transfers t ON t.id = a.transfer_id
                ORDER BY a.occurred_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """, (row, index) -> new Alert(row.getLong("id"), row.getString("reference"),
                row.getLong("sender_wallet_id"), TransferRiskRule.valueOf(row.getString("rule")),
                row.getObject("occurred_at", OffsetDateTime.class)), limit, offset);
    }

    public record DailyUsage(BigInteger amount, long count) {}
    public record RecentUsage(long count, long recipients, boolean recipientSeen) {}
    public record Alert(long id, String transferReference, long senderWalletId,
                        TransferRiskRule rule, OffsetDateTime occurredAt) {}
}
