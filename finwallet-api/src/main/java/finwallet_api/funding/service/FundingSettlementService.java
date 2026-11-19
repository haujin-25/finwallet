package finwallet_api.funding.service;

import finwallet_api.common.exception.CurrencyMismatchException;
import finwallet_api.common.exception.ResourceNotFoundException;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingOperation;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.provider.PaymentProvider;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.repository.FundingJournalRepository;
import finwallet_api.funding.repository.FundingOperationRepository;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundingSettlementService {

    private final FundingOperationRepository operations;
    private final WalletRepository wallets;
    private final FundingJournalRepository journal;
    private final PaymentProvider provider;

    public FundingSettlementService(FundingOperationRepository operations, WalletRepository wallets,
                                    FundingJournalRepository journal, PaymentProvider provider) {
        this.operations = operations;
        this.wallets = wallets;
        this.journal = journal;
        this.provider = provider;
    }

    /**
     * Internal simulator entry point. Only trusted application code selects a scenario.
     * Call outside wallet-writing transactions. The simulator performs no network I/O.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public FundingResponse checkStatus(String reference, Scenario scenario) {
        Objects.requireNonNull(scenario);
        Long walletId = operations.findWalletIdByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Funding operation not found"));
        Wallet wallet = wallets.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        // Every funding writer takes the wallet lock before inspecting or changing operation state.
        FundingOperation operation = operations.findByWalletIdAndReference(walletId, reference)
                .orElseThrow(() -> new ResourceNotFoundException("Funding operation not found"));

        var confirmation = provider.verify(operation.getProviderReference(), scenario);
        if (!operation.getProviderReference().equals(confirmation.transactionReference())) {
            throw new FundingStateConflictException("Confirmation refers to a different operation");
        }
        Scenario outcome = Objects.requireNonNull(confirmation.outcome());
        if (outcome == Scenario.TIMEOUT) {
            return FundingResponse.from(operation);
        }

        FundingStatus target = outcome == Scenario.SUCCESS
                ? FundingStatus.COMPLETED : FundingStatus.FAILED;
        String event = Objects.requireNonNull(confirmation.eventReference());
        if (operation.getStatus() != FundingStatus.PENDING) {
            if (operation.getStatus() == target && event.equals(operation.getProviderEventReference())) {
                return FundingResponse.from(operation);
            }
            throw new FundingStateConflictException("Funding operation already resolved");
        }

        if (!operation.getCurrency().equals(wallet.getCurrency())) {
            throw new CurrencyMismatchException("Wallet currency differs from the funding operation");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        applyWalletEffect(wallet, operation, target, now);
        operation.resolve(target, event, now);
        if (target == FundingStatus.COMPLETED) {
            journal.append(operation);
        }
        return FundingResponse.from(operation);
    }

    private void applyWalletEffect(Wallet wallet, FundingOperation operation, FundingStatus target,
                                   OffsetDateTime now) {
        if (operation.getOperationType() == FundingType.WITHDRAWAL) {
            wallet.release(operation.getAmount());
            if (target == FundingStatus.COMPLETED) {
                wallet.setBalance(wallet.getBalance() - operation.getAmount());
            }
            wallet.setUpdatedAt(now);
        } else if (target == FundingStatus.COMPLETED) {
            wallet.setBalance(Math.addExact(wallet.getBalance(), operation.getAmount()));
            wallet.setUpdatedAt(now);
        }
    }
}
