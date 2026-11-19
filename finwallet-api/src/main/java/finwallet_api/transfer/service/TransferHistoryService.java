package finwallet_api.transfer.service;

import finwallet_api.transfer.dto.TransferDirection;
import finwallet_api.transfer.dto.TransferHistoryItem;
import finwallet_api.transfer.dto.TransferHistoryPage;
import finwallet_api.transfer.repository.TransferRepository;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferHistoryService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;

    public TransferHistoryService(WalletRepository walletRepository, TransferRepository transferRepository) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransferHistoryPage history(Long userId, TransferDirection direction,
            OffsetDateTime fromInclusive, OffsetDateTime toExclusive, Pageable pageable) {
        Page<TransferHistoryItem> history = walletRepository.findIdByUserId(userId)
                .map(walletId -> transferRepository.findHistory(walletId,
                        direction != TransferDirection.INCOMING, direction != TransferDirection.OUTGOING,
                        fromInclusive, toExclusive, pageable))
                .orElseGet(() -> Page.empty(pageable));

        return new TransferHistoryPage(history.getContent(), history.getNumber(), history.getSize(),
                history.getTotalElements(), history.getTotalPages(), history.hasNext());
    }
}
