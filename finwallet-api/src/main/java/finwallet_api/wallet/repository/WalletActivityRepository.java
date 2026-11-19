package finwallet_api.wallet.repository;

import finwallet_api.wallet.dto.WalletActivityPage.Item;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletActivityRepository {

    private final JdbcTemplate jdbc;

    public WalletActivityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Item> findByWallet(Long walletId, long offset, int limit) {
        return jdbc.query("""
                SELECT reference, operation_type, direction, amount, currency, status, description, created_at
                FROM (
                    SELECT t.reference, t.id, 0 AS source,
                           CASE WHEN t.reversal_of_transfer_id IS NULL THEN 'TRANSFER' ELSE 'REVERSAL' END AS operation_type,
                           CASE WHEN t.sender_wallet_id = ? THEN 'OUTGOING' ELSE 'INCOMING' END AS direction,
                           t.amount, t.currency, t.status, t.description, t.created_at
                    FROM transfers t
                    WHERE t.sender_wallet_id = ? OR t.recipient_wallet_id = ?
                    UNION ALL
                    SELECT f.reference, f.id, 1 AS source, f.operation_type,
                           CASE WHEN f.operation_type = 'DEPOSIT' THEN 'INCOMING' ELSE 'OUTGOING' END,
                           f.amount, f.currency, f.status, f.description, f.created_at
                    FROM funding_operations f
                    WHERE f.wallet_id = ?
                ) activity
                ORDER BY created_at DESC, source DESC, id DESC
                LIMIT ? OFFSET ?
                """, (row, index) -> new Item(row.getString("reference"), row.getString("operation_type"),
                row.getString("direction"), row.getLong("amount"), row.getString("currency"),
                row.getString("status"), row.getString("description"), row.getObject("created_at", OffsetDateTime.class)),
                walletId, walletId, walletId, walletId, limit, offset);
    }
}
