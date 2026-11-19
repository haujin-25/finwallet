package finwallet_api.reconciliation.dto;

public record ReconciliationDiscrepancy(
        Type type,
        Long walletId,
        Long transferId,
        Long ledgerEntryId,
        String detail
) {
    public enum Type {
        BALANCE_MISMATCH,
        MISSING_DEBIT,
        MISSING_CREDIT,
        DUPLICATE_DEBIT,
        DUPLICATE_CREDIT,
        ORPHANED_TRANSFER_REFERENCE,
        ORPHANED_WALLET_REFERENCE,
        UNEXPECTED_ENTRY,
        WRONG_WALLET,
        AMOUNT_MISMATCH,
        CURRENCY_MISMATCH,
        INVALID_REVERSAL_ORIGINAL,
        DUPLICATE_REVERSAL,
        REVERSAL_DIRECTION_MISMATCH,
        REVERSAL_AMOUNT_MISMATCH,
        REVERSAL_CURRENCY_MISMATCH,
        RESERVATION_MISMATCH,
        FUNDING_OPERATION_MISMATCH,
        FUNDING_ENTRY_MISMATCH,
        SETTLEMENT_ACCOUNT_MISMATCH
    }
}
