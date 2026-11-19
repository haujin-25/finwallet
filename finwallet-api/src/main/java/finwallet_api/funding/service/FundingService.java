package finwallet_api.funding.service;

import finwallet_api.common.exception.CurrencyMismatchException;
import finwallet_api.common.exception.IdempotencyConflictException;
import finwallet_api.common.exception.InactiveWalletException;
import finwallet_api.common.exception.InsufficientFundsException;
import finwallet_api.common.exception.ResourceNotFoundException;
import finwallet_api.funding.dto.FundingRequest;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingOperation;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.repository.FundingOperationRepository;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundingService {

    private final FundingOperationRepository operations;
    private final WalletRepository wallets;

    public FundingService(FundingOperationRepository operations, WalletRepository wallets) {
        this.operations = operations;
        this.wallets = wallets;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FundingResponse request(Long userId, FundingType type, FundingRequest request) {
        Long walletId = walletId(userId);
        Wallet wallet = wallets.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        var existing = operations.findByWalletIdAndOperationTypeAndIdempotencyKey(
                walletId, type, request.idempotencyKey());
        if (existing.isPresent()) {
            FundingOperation original = existing.get();
            if (original.getAmount() != request.amount()
                    || !Objects.equals(original.getDescription(), request.description())) {
                throw new IdempotencyConflictException("Idempotency key already used for a different funding request");
            }
            return FundingResponse.from(original);
        }

        if (!"ACTIVE".equals(wallet.getStatus())) {
            throw new InactiveWalletException("Wallet is not active");
        }
        if (!FundingOperation.CURRENCY.equals(wallet.getCurrency())) {
            throw new CurrencyMismatchException("Funding requires a MYR wallet");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        FundingOperation operation = new FundingOperation(
                walletId, type, request.amount(), request.description(), request.idempotencyKey(), now);
        if (type == FundingType.WITHDRAWAL) {
            if (wallet.getAvailableBalance() < request.amount()) {
                throw new InsufficientFundsException("Insufficient available funds");
            }
            wallet.reserve(request.amount());
            wallet.setUpdatedAt(now);
        }
        return FundingResponse.from(operations.save(operation));
    }

    @Transactional(readOnly = true)
    public FundingResponse get(Long userId, String reference) {
        return operations.findByWalletIdAndReference(walletId(userId), reference)
                .map(FundingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Funding operation not found"));
    }

    private Long walletId(Long userId) {
        return wallets.findIdByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }
}
