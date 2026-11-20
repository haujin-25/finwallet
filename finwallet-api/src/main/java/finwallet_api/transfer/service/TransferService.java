package finwallet_api.transfer.service;

import finwallet_api.common.exception.CurrencyMismatchException;
import finwallet_api.common.exception.IdempotencyConflictException;
import finwallet_api.common.exception.InactiveWalletException;
import finwallet_api.common.exception.InsufficientFundsException;
import finwallet_api.common.exception.ResourceNotFoundException;
import finwallet_api.common.exception.SelfTransferException;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.dto.TransferResponse;
import finwallet_api.transfer.entity.LedgerEntry;
import finwallet_api.transfer.entity.Transfer;
import finwallet_api.transfer.repository.LedgerEntryRepository;
import finwallet_api.transfer.repository.TransferRepository;
import finwallet_api.transfer.risk.TransferPolicy;
import finwallet_api.transfer.risk.TransferPostingTime;
import finwallet_api.transfer.risk.TransferRiskRule;
import finwallet_api.user.repository.UserRepository;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransferPolicy policy;
    private final TransferPostingTime postingTime;

    public TransferService(
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletRepository walletRepository,
            UserRepository userRepository,
            TransferPolicy policy,
            TransferPostingTime postingTime
    ) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.postingTime = postingTime;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transfer(Long senderUserId, TransferRequest request) {
        // Resolve IDs without placing unlocked Wallet entities in the persistence context.
        Long senderWalletId = walletRepository.findIdByUserId(senderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender wallet not found"));
        var recipientUser = userRepository.findByEmail(request.recipientEmail());
        Long recipientWalletId = recipientUser
                .flatMap(user -> walletRepository.findIdByUserId(user.getId()))
                .orElse(null);

        LockedWallets wallets = lockWallets(senderWalletId, recipientWalletId);

        // The sender lock is held through commit. A waiting request sees the
        // committed transfer before it evaluates the current wallet balances.
        Optional<Transfer> existing = transferRepository.findBySenderWalletIdAndIdempotencyKey(
                senderWalletId, request.idempotencyKey());
        if (existing.isPresent()) {
            Transfer original = existing.get();
            if (!original.getRecipientWalletId().equals(recipientWalletId)
                    || !original.getAmount().equals(request.amount())
                    || !Objects.equals(original.getDescription(), request.description())) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used for a different transfer");
            }
            return toResponse(original);
        }

        if (recipientWalletId == null) {
            throw new ResourceNotFoundException(recipientUser.isEmpty()
                    ? "No user found with email: " + request.recipientEmail()
                    : "Recipient wallet not found");
        }
        if (senderWalletId.equals(recipientWalletId)) {
            throw new SelfTransferException("Cannot transfer money to your own wallet");
        }

        return post(wallets, new Transfer(generateReference(), senderWalletId, recipientWalletId,
                request.amount(), wallets.sender().getCurrency(), request.description(), request.idempotencyKey(),
                postingTime.now()));
    }

    /**
     * Internal operation for trusted callers. The original transfer reference is the retry key.
     * Invoke outside a wallet-writing transaction so this transaction owns the wallet locks.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public TransferResponse reverse(String originalReference) {
        if (originalReference == null || originalReference.isBlank()) {
            throw new IllegalArgumentException("Original transfer reference is required");
        }
        Transfer original = transferRepository.findByReference(originalReference)
                .orElseThrow(() -> new ResourceNotFoundException("Original transfer not found"));
        if (original.getReversalOfTransferId() != null || !"COMPLETED".equals(original.getStatus())) {
            throw new TransferNotReversibleException("Only a completed original transfer can be reversed");
        }

        LockedWallets wallets = lockWallets(original.getRecipientWalletId(), original.getSenderWalletId());
        // The first attempt holds both locks through commit. READ COMMITTED lets
        // a waiting attempt see the committed reversal before checking funds.
        return transferRepository.findByReversalOfTransferId(original.getId())
                .map(this::toResponse)
                .orElseGet(() -> post(wallets, Transfer.reversalOf(generateReference(), original, postingTime.now())));
    }

    private TransferResponse post(LockedWallets wallets, Transfer transfer) {
        Wallet sender = wallets.sender();
        Wallet recipient = wallets.recipient();
        if (!"ACTIVE".equals(sender.getStatus())) {
            throw new InactiveWalletException("Sender wallet is not active");
        }
        if (!"ACTIVE".equals(recipient.getStatus())) {
            throw new InactiveWalletException("Recipient wallet is not active");
        }
        if (!sender.getCurrency().equals(recipient.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Currency mismatch: sender uses " + sender.getCurrency()
                    + ", recipient uses " + recipient.getCurrency());
        }
        if (!transfer.getCurrency().equals(sender.getCurrency())) {
            throw new CurrencyMismatchException("Wallet currency does not match the original transfer");
        }

        long amount = transfer.getAmount();
        if (sender.getAvailableBalance() < amount) {
            throw new InsufficientFundsException("Insufficient funds in sender wallet");
        }
        if (Long.MAX_VALUE - recipient.getBalance() < amount) {
            throw new ArithmeticException("Recipient balance overflow");
        }

        List<TransferRiskRule> alerts = transfer.getReversalOfTransferId() == null
                ? policy.evaluate(transfer) : List.of();
        Transfer posted = transferRepository.saveAndFlush(transfer);
        sender.setBalance(sender.getBalance() - amount);
        sender.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(sender);

        recipient.setBalance(recipient.getBalance() + amount);
        recipient.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(recipient);

        ledgerEntryRepository.save(LedgerEntry.debitFor(posted));
        ledgerEntryRepository.save(LedgerEntry.creditFor(posted));
        policy.record(posted, alerts);
        return toResponse(posted);
    }

    private LockedWallets lockWallets(Long senderId, Long recipientId) {
        if (recipientId == null || senderId.equals(recipientId)) {
            return new LockedWallets(lockWallet(senderId), null);
        }
        Wallet lower = lockWallet(Math.min(senderId, recipientId));
        Wallet higher = lockWallet(Math.max(senderId, recipientId));
        return senderId.equals(lower.getId()) ? new LockedWallets(lower, higher) : new LockedWallets(higher, lower);
    }

    private record LockedWallets(Wallet sender, Wallet recipient) {
    }

    private Wallet lockWallet(long walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }

    private String generateReference() {
        String date = LocalDate.now().toString().replace("-", "");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TRF-" + date + "-" + suffix;
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getReference(),
                transfer.getSenderWalletId(),
                transfer.getRecipientWalletId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getDescription(),
                transfer.getCreatedAt()
        );
    }
}
