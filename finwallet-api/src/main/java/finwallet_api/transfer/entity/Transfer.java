package finwallet_api.transfer.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @Column(name = "sender_wallet_id", nullable = false)
    private Long senderWalletId;

    @Column(name = "recipient_wallet_id", nullable = false)
    private Long recipientWalletId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "reversal_of_transfer_id")
    private Long reversalOfTransferId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Transfer() {
    }

    public Transfer(String reference, Long senderWalletId, Long recipientWalletId, long amount,
                    String currency, String description, String idempotencyKey, OffsetDateTime createdAt) {
        this(reference, senderWalletId, recipientWalletId, amount, currency, description,
                Objects.requireNonNull(idempotencyKey), createdAt, null);
    }

    private Transfer(String reference, Long senderWalletId, Long recipientWalletId, long amount,
                     String currency, String description, String idempotencyKey, OffsetDateTime createdAt,
                     Long reversalOfTransferId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        this.reference = reference;
        this.senderWalletId = senderWalletId;
        this.recipientWalletId = recipientWalletId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = "COMPLETED";
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.reversalOfTransferId = reversalOfTransferId;
    }

    public static Transfer reversalOf(String reference, Transfer original, OffsetDateTime createdAt) {
        return new Transfer(reference, original.recipientWalletId, original.senderWalletId, original.amount,
                original.currency, null, null, createdAt, Objects.requireNonNull(original.id));
    }

    public Long getReversalOfTransferId() {
        return reversalOfTransferId;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Long getSenderWalletId() {
        return senderWalletId;
    }

    public Long getRecipientWalletId() {
        return recipientWalletId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
