package finwallet_api.transfer.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "entry_type", nullable = false, length = 10)
    private String entryType;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(Transfer transfer, Long walletId, String entryType) {
        this.transferId = Objects.requireNonNull(transfer.getId(), "Transfer must be persisted");
        this.walletId = walletId;
        this.entryType = entryType;
        this.amount = transfer.getAmount();
        this.currency = transfer.getCurrency();
        this.createdAt = transfer.getCreatedAt();
    }

    public static LedgerEntry debitFor(Transfer transfer) {
        return new LedgerEntry(transfer, transfer.getSenderWalletId(), "DEBIT");
    }

    public static LedgerEntry creditFor(Transfer transfer) {
        return new LedgerEntry(transfer, transfer.getRecipientWalletId(), "CREDIT");
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public String getEntryType() {
        return entryType;
    }

    public Long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
