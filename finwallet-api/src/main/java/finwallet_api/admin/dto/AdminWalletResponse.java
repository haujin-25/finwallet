package finwallet_api.admin.dto;

import finwallet_api.wallet.entity.Wallet;

public record AdminWalletResponse(long walletId, String currency, long balance, long reservedBalance,
                                  long availableBalance, String status) {

    public static AdminWalletResponse from(Wallet wallet) {
        return new AdminWalletResponse(wallet.getId(), wallet.getCurrency(), wallet.getBalance(),
                wallet.getReservedBalance(), wallet.getAvailableBalance(), wallet.getStatus());
    }
}
