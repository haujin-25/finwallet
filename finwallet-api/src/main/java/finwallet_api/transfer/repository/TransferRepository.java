package finwallet_api.transfer.repository;

import finwallet_api.transfer.dto.TransferHistoryItem;
import finwallet_api.transfer.entity.Transfer;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    String HISTORY_FILTERS = """
            where ((:includeOutgoing = true and t.senderWalletId = :walletId)
                or (:includeIncoming = true and t.recipientWalletId = :walletId))
              and (cast(:fromInclusive as OffsetDateTime) is null or t.createdAt >= :fromInclusive)
              and (cast(:toExclusive as OffsetDateTime) is null or t.createdAt < :toExclusive)
            """;

    @Query(value = """
            select new finwallet_api.transfer.dto.TransferHistoryItem(
                t.reference,
                case when t.senderWalletId = :walletId then 'OUTGOING' else 'INCOMING' end,
                t.amount, t.currency, t.status, t.description, counterparty.fullName, t.createdAt,
                case when t.reversalOfTransferId is null then 'TRANSFER' else 'REVERSAL' end,
                original.reference)
            from Transfer t
            join Wallet counterpartyWallet on counterpartyWallet.id =
                case when t.senderWalletId = :walletId then t.recipientWalletId else t.senderWalletId end
            join User counterparty on counterparty.id = counterpartyWallet.userId
            left join Transfer original on original.id = t.reversalOfTransferId
            """ + HISTORY_FILTERS + "order by t.createdAt desc, t.id desc",
            countQuery = "select count(t) from Transfer t " + HISTORY_FILTERS)
    Page<TransferHistoryItem> findHistory(
            @Param("walletId") Long walletId,
            @Param("includeOutgoing") boolean includeOutgoing,
            @Param("includeIncoming") boolean includeIncoming,
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toExclusive") OffsetDateTime toExclusive,
            Pageable pageable);

    Optional<Transfer> findByReversalOfTransferId(Long originalId);

    Optional<Transfer> findByReference(String reference);

    Optional<Transfer> findBySenderWalletIdAndIdempotencyKey(Long senderWalletId, String idempotencyKey);
}
