package finwallet_api.wallet.service;

import finwallet_api.wallet.dto.WalletActivityPage;
import finwallet_api.wallet.repository.WalletActivityRepository;
import finwallet_api.wallet.repository.WalletRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletActivityService {

    private final WalletRepository wallets;
    private final WalletActivityRepository activity;

    public WalletActivityService(WalletRepository wallets, WalletActivityRepository activity) {
        this.wallets = wallets;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public WalletActivityPage forUser(Long userId, int page, int size) {
        List<WalletActivityPage.Item> items = wallets.findIdByUserId(userId)
                .map(walletId -> activity.findByWallet(walletId, (long) page * size, size + 1))
                .orElseGet(List::of);
        boolean hasNext = items.size() > size;
        return new WalletActivityPage(hasNext ? List.copyOf(items.subList(0, size)) : items, page, size, hasNext);
    }
}
