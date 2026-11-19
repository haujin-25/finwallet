package finwallet_api.reconciliation.repository;

import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy;
import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy.Type;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FundingReconciliationRepository {

    private final JdbcTemplate jdbc;

    public FundingReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReconciliationDiscrepancy> findReservationDiscrepancies() {
        return jdbc.query("""
                WITH holds AS (
                    SELECT wallet_id, sum(amount) AS expected
                    FROM funding_operations
                    WHERE operation_type = 'WITHDRAWAL' AND status = 'PENDING'
                    GROUP BY wallet_id
                )
                SELECT w.id, w.reserved_balance, coalesce(h.expected, 0) AS expected
                FROM wallets w LEFT JOIN holds h ON h.wallet_id = w.id
                WHERE w.reserved_balance <> coalesce(h.expected, 0)
                   OR w.reserved_balance < 0 OR w.reserved_balance > w.balance
                ORDER BY w.id
                """, (row, index) -> new ReconciliationDiscrepancy(Type.RESERVATION_MISMATCH,
                row.getLong("id"), null, null,
                "Reserved " + row.getLong("reserved_balance") + ", expected " + row.getBigDecimal("expected")));
    }

    public List<ReconciliationDiscrepancy> findOperationDiscrepancies() {
        return jdbc.query("""
                SELECT f.id, f.wallet_id
                FROM funding_operations f
                LEFT JOIN wallets w ON w.id = f.wallet_id
                LEFT JOIN funding_ledger_entries e ON e.operation_id = f.id
                GROUP BY f.id, w.id, w.currency
                HAVING w.id IS NULL OR w.currency <> f.currency
                    OR (f.status = 'COMPLETED' AND (
                        count(e.id) FILTER (WHERE e.entry_type = 'DEBIT') <> 1
                        OR count(e.id) FILTER (WHERE e.entry_type = 'CREDIT') <> 1
                        OR count(e.id) <> 2))
                    OR (f.status <> 'COMPLETED' AND count(e.id) <> 0)
                ORDER BY f.id
                """, (row, index) -> new ReconciliationDiscrepancy(Type.FUNDING_OPERATION_MISMATCH,
                row.getLong("wallet_id"), null, null,
                "Funding operation " + row.getLong("id") + " has invalid wallet, currency, or journal count"));
    }

    public List<ReconciliationDiscrepancy> findJournalDiscrepancies() {
        return jdbc.query("""
                SELECT e.id, e.operation_id, e.wallet_id
                FROM funding_ledger_entries e
                LEFT JOIN funding_operations f ON f.id = e.operation_id
                LEFT JOIN funding_accounts a ON a.code = e.account_code
                LEFT JOIN wallets w ON w.id = e.wallet_id
                WHERE f.id IS NULL OR f.status <> 'COMPLETED'
                    OR e.amount <= 0 OR e.amount <> f.amount OR e.currency <> f.currency
                    OR e.entry_type NOT IN ('DEBIT', 'CREDIT')
                    OR CASE WHEN (f.operation_type = 'DEPOSIT' AND e.entry_type = 'CREDIT')
                              OR (f.operation_type = 'WITHDRAWAL' AND e.entry_type = 'DEBIT')
                            THEN e.wallet_id IS DISTINCT FROM f.wallet_id OR e.account_code IS NOT NULL
                                OR w.id IS NULL OR w.currency <> e.currency
                            ELSE e.wallet_id IS NOT NULL OR a.code IS DISTINCT FROM 'SIMULATED_MYR_SETTLEMENT'
                                OR a.currency <> e.currency
                       END
                ORDER BY e.id
                """, (row, index) -> new ReconciliationDiscrepancy(Type.FUNDING_ENTRY_MISMATCH,
                row.getObject("wallet_id", Long.class), null, null,
                "Funding entry " + row.getLong("id") + " disagrees with operation " + row.getLong("operation_id")));
    }

    public List<ReconciliationDiscrepancy> findSettlementDiscrepancies() {
        return jdbc.query("""
                WITH position AS (
                    SELECT coalesce(sum(CASE entry_type WHEN 'CREDIT' THEN amount::numeric
                                           WHEN 'DEBIT' THEN -amount::numeric END), 0) AS posted
                    FROM funding_ledger_entries
                    WHERE account_code = 'SIMULATED_MYR_SETTLEMENT' AND currency = 'MYR'
                ), completed AS (
                    SELECT coalesce(sum(CASE operation_type WHEN 'WITHDRAWAL' THEN amount::numeric
                                           WHEN 'DEPOSIT' THEN -amount::numeric END), 0) AS expected
                    FROM funding_operations
                    WHERE status = 'COMPLETED' AND currency = 'MYR'
                )
                SELECT posted, expected
                FROM position CROSS JOIN completed
                WHERE posted <> expected OR NOT EXISTS (
                    SELECT 1 FROM funding_accounts WHERE code = 'SIMULATED_MYR_SETTLEMENT' AND currency = 'MYR'
                )
                """, (row, index) -> new ReconciliationDiscrepancy(Type.SETTLEMENT_ACCOUNT_MISMATCH,
                null, null, null, "Simulated MYR settlement account missing or out of balance. Posted "
                        + row.getBigDecimal("posted") + ", expected " + row.getBigDecimal("expected")));
    }
}
