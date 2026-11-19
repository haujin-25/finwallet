package finwallet_api.funding.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "funding_operations")
public class FundingOperation {

    public static final String CURRENCY = "MYR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String reference;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 12, updatable = false)
    private FundingType operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private FundingStatus status;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(length = 500, updatable = false)
    private String description;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "provider_reference", nullable = false, length = 80, updatable = false)
    private String providerReference;

    @Column(name = "provider_event_reference", length = 100)
    private String providerEventReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected FundingOperation() {
    }

    public FundingOperation(Long walletId, FundingType type, long amount, String description,
                            String idempotencyKey, OffsetDateTime createdAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.walletId = Objects.requireNonNull(walletId);
        this.operationType = Objects.requireNonNull(type);
        this.reference = (type == FundingType.DEPOSIT ? "DEP-" : "WDR-") + UUID.randomUUID();
        this.providerReference = "SIM:" + reference;
        this.status = FundingStatus.PENDING;
        this.amount = amount;
        this.currency = CURRENCY;
        this.description = description;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public void resolve(FundingStatus outcome, String eventReference, OffsetDateTime resolvedAt) {
        if (status != FundingStatus.PENDING || (outcome != FundingStatus.COMPLETED && outcome != FundingStatus.FAILED)) {
            throw new IllegalStateException("Invalid funding status transition");
        }
        this.providerEventReference = Objects.requireNonNull(eventReference);
        this.resolvedAt = Objects.requireNonNull(resolvedAt);
        this.status = outcome;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getWalletId() { return walletId; }
    public FundingType getOperationType() { return operationType; }
    public FundingStatus getStatus() { return status; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getProviderReference() { return providerReference; }
    public String getProviderEventReference() { return providerEventReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
}
