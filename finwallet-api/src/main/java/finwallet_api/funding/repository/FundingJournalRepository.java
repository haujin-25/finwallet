package finwallet_api.funding.repository;

import finwallet_api.funding.entity.FundingOperation;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FundingJournalRepository {

    private static final String SETTLEMENT_ACCOUNT = "SIMULATED_MYR_SETTLEMENT";
    private final JdbcTemplate jdbc;

    public FundingJournalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(FundingOperation operation) {
        if (operation.getStatus() != FundingStatus.COMPLETED) {
            throw new IllegalArgumentException("Only completed funding can be journaled");
        }
        String walletEntry = operation.getOperationType() == FundingType.DEPOSIT ? "CREDIT" : "DEBIT";
        String systemEntry = operation.getOperationType() == FundingType.DEPOSIT ? "DEBIT" : "CREDIT";
        jdbc.update("""
                INSERT INTO funding_ledger_entries
                    (operation_id, wallet_id, account_code, entry_type, amount, currency, created_at)
                VALUES (?, ?, NULL, ?, ?, ?, ?), (?, NULL, ?, ?, ?, ?, ?)
                """, operation.getId(), operation.getWalletId(), walletEntry, operation.getAmount(),
                operation.getCurrency(), operation.getResolvedAt(), operation.getId(), SETTLEMENT_ACCOUNT,
                systemEntry, operation.getAmount(), operation.getCurrency(), operation.getResolvedAt());
    }
}
