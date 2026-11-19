package finwallet_api.reconciliation.repository;

import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy;
import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy.Type;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationRepository {

    private final JdbcTemplate jdbc;

    public ReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot countRecords() {
        return jdbc.queryForObject("""
                SELECT statement_timestamp() AS checked_at,
                       (SELECT count(*) FROM wallets) AS wallet_count,
                       (SELECT count(*) FROM transfers) AS transfer_count
                """, (row, index) -> new Snapshot(row.getObject("checked_at", OffsetDateTime.class),
                row.getLong("wallet_count"), row.getLong("transfer_count")));
    }

    public List<ReconciliationDiscrepancy> findBalanceDiscrepancies() {
        return jdbc.query("""
                WITH entries AS (
                    SELECT wallet_id, currency, entry_type, amount FROM ledger_entries
                    UNION ALL
                    SELECT e.wallet_id, e.currency, e.entry_type, e.amount
                    FROM funding_ledger_entries e
                    JOIN funding_operations f ON f.id = e.operation_id AND f.status = 'COMPLETED'
                    WHERE e.wallet_id IS NOT NULL
                ), movements AS (
                    SELECT wallet_id, currency,
                           sum(CASE entry_type
                               WHEN 'CREDIT' THEN amount::numeric
                               WHEN 'DEBIT' THEN -amount::numeric
                           END) AS net
                    FROM entries
                    GROUP BY wallet_id, currency
                )
                SELECT w.id, w.balance, coalesce(m.net, 0) AS expected
                FROM wallets w
                LEFT JOIN movements m ON m.wallet_id = w.id AND m.currency = w.currency
                WHERE w.balance <> coalesce(m.net, 0)
                ORDER BY w.id
                """, (row, index) -> new ReconciliationDiscrepancy(Type.BALANCE_MISMATCH,
                row.getLong("id"), null, null,
                "Stored " + row.getLong("balance") + ", expected "
                        + row.getBigDecimal("expected").toBigIntegerExact() + " from zero opening balance"));
    }

    public List<ReconciliationDiscrepancy> findTransferDiscrepancies() {
        return jdbc.query("""
                WITH entry_counts AS (
                    SELECT t.id,
                           count(*) FILTER (WHERE e.entry_type = 'DEBIT') AS debit_count,
                           count(*) FILTER (WHERE e.entry_type = 'CREDIT') AS credit_count
                    FROM transfers t
                    LEFT JOIN ledger_entries e ON e.transfer_id = t.id
                    WHERE t.status = 'COMPLETED'
                    GROUP BY t.id
                )
                SELECT c.id, issue.type, issue.entry_count
                FROM entry_counts c
                CROSS JOIN LATERAL (VALUES
                    ('MISSING_DEBIT', c.debit_count, c.debit_count = 0),
                    ('MISSING_CREDIT', c.credit_count, c.credit_count = 0),
                    ('DUPLICATE_DEBIT', c.debit_count, c.debit_count > 1),
                    ('DUPLICATE_CREDIT', c.credit_count, c.credit_count > 1)
                ) AS issue(type, entry_count, detected)
                WHERE issue.detected
                ORDER BY c.id, issue.type
                """, (row, index) -> new ReconciliationDiscrepancy(Type.valueOf(row.getString("type")),
                null, row.getLong("id"), null, "Entry count: " + row.getLong("entry_count")));
    }

    public List<ReconciliationDiscrepancy> findEntryDiscrepancies() {
        return jdbc.query("""
                SELECT e.id, e.wallet_id, e.transfer_id, issue.type, issue.detail
                FROM ledger_entries e
                LEFT JOIN transfers t ON t.id = e.transfer_id
                LEFT JOIN wallets w ON w.id = e.wallet_id
                CROSS JOIN LATERAL (VALUES
                    ('ORPHANED_TRANSFER_REFERENCE', 'Transfer is missing', t.id IS NULL),
                    ('ORPHANED_WALLET_REFERENCE', 'Wallet is missing', w.id IS NULL),
                    ('UNEXPECTED_ENTRY', 'Entry must be a debit or credit for a completed transfer',
                        e.entry_type NOT IN ('DEBIT', 'CREDIT') OR t.status <> 'COMPLETED'),
                    ('WRONG_WALLET', 'Debit must use the sender and credit must use the recipient',
                        (e.entry_type = 'DEBIT' AND e.wallet_id <> t.sender_wallet_id)
                        OR (e.entry_type = 'CREDIT' AND e.wallet_id <> t.recipient_wallet_id)),
                    ('AMOUNT_MISMATCH', 'Entry must match the positive transfer amount',
                        e.amount <= 0 OR e.amount <> t.amount),
                    ('CURRENCY_MISMATCH', 'Entry currency must match the transfer and the wallet',
                        e.currency <> t.currency OR e.currency <> w.currency)
                ) AS issue(type, detail, detected)
                WHERE issue.detected
                ORDER BY e.id, issue.type
                """, (row, index) -> new ReconciliationDiscrepancy(Type.valueOf(row.getString("type")),
                row.getLong("wallet_id"), row.getLong("transfer_id"), row.getLong("id"),
                row.getString("detail")));
    }

    public List<ReconciliationDiscrepancy> findReversalDiscrepancies() {
        return jdbc.query("""
                WITH reversals AS (
                    SELECT t.*, count(*) OVER (PARTITION BY reversal_of_transfer_id) AS reversal_count
                    FROM transfers t
                    WHERE reversal_of_transfer_id IS NOT NULL
                )
                SELECT r.id, r.reversal_of_transfer_id, issue.type
                FROM reversals r
                LEFT JOIN transfers original ON original.id = r.reversal_of_transfer_id
                CROSS JOIN LATERAL (VALUES
                    ('INVALID_REVERSAL_ORIGINAL', original.id IS NULL OR original.reversal_of_transfer_id IS NOT NULL
                        OR original.status <> 'COMPLETED' OR r.status <> 'COMPLETED' OR r.id = original.id),
                    ('DUPLICATE_REVERSAL', r.reversal_count > 1),
                    ('REVERSAL_DIRECTION_MISMATCH', r.sender_wallet_id <> original.recipient_wallet_id
                        OR r.recipient_wallet_id <> original.sender_wallet_id),
                    ('REVERSAL_AMOUNT_MISMATCH', r.amount <> original.amount),
                    ('REVERSAL_CURRENCY_MISMATCH', r.currency <> original.currency)
                ) AS issue(type, detected)
                WHERE issue.detected
                ORDER BY r.id, issue.type
                """, (row, index) -> new ReconciliationDiscrepancy(Type.valueOf(row.getString("type")),
                null, row.getLong("id"), null, "Linked original transfer ID: " + row.getLong("reversal_of_transfer_id")));
    }

    public record Snapshot(OffsetDateTime timestamp, long wallets, long transfers) {
    }
}
