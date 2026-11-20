package finwallet_api.transfer;

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
import finwallet_api.transfer.service.TransferNotReversibleException;
import finwallet_api.transfer.service.TransferService;
import finwallet_api.user.entity.User;
import finwallet_api.user.repository.UserRepository;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import finwallet_api.transfer.risk.TransferPolicy;
import finwallet_api.transfer.risk.TransferPostingTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransferPolicy policy;

    @Mock
    private TransferPostingTime postingTime;

    private TransferService transferService;

    private Wallet senderWallet;
    private Wallet recipientWallet;
    private User recipientUser;

    @BeforeEach
    void setUp() {
        lenient().when(postingTime.now()).thenReturn(OffsetDateTime.now());
        transferService = new TransferService(
                transferRepository, ledgerEntryRepository, walletRepository, userRepository, policy, postingTime);

        senderWallet = new Wallet();
        senderWallet.setId(1L);
        senderWallet.setUserId(10L);
        senderWallet.setCurrency("MYR");
        senderWallet.setBalance(10000L);
        senderWallet.setStatus("ACTIVE");
        senderWallet.setCreatedAt(OffsetDateTime.now());
        senderWallet.setUpdatedAt(OffsetDateTime.now());

        recipientUser = new User();
        recipientUser.setId(20L);
        recipientUser.setEmail("recipient@example.com");

        recipientWallet = new Wallet();
        recipientWallet.setId(2L);
        recipientWallet.setUserId(20L);
        recipientWallet.setCurrency("MYR");
        recipientWallet.setBalance(5000L);
        recipientWallet.setStatus("ACTIVE");
        recipientWallet.setCreatedAt(OffsetDateTime.now());
        recipientWallet.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    void transfer_success_returnsTransferResponse() {
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 5000L, "Dinner", "key-001");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.findBySenderWalletIdAndIdempotencyKey(1L, "key-001"))
                .thenReturn(Optional.empty());

        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferResponse response = transferService.transfer(10L, request);

        assertThat(response.amount()).isEqualTo(5000L);
        assertThat(response.currency()).isEqualTo("MYR");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(senderWallet.getBalance()).isEqualTo(5000L);
        assertThat(recipientWallet.getBalance()).isEqualTo(10000L);

        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entries.capture());
        assertThat(entries.getAllValues())
                .allSatisfy(entry -> {
                    assertThat(entry.getTransferId()).isEqualTo(100L);
                    assertThat(entry.getAmount()).isEqualTo(5000L);
                    assertThat(entry.getCurrency()).isEqualTo("MYR");
                })
                .extracting(LedgerEntry::getEntryType, LedgerEntry::getWalletId)
                .containsExactly(tuple("DEBIT", 1L), tuple("CREDIT", 2L));
    }

    @Test
    void transfer_insufficientFunds_throwsException() {
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 99999L, null, "key-002");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.findBySenderWalletIdAndIdempotencyKey(1L, "key-002"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(InsufficientFundsException.class);

        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_selfTransfer_throwsException() {
        recipientUser.setId(10L);

        TransferRequest request = new TransferRequest(
                "recipient@example.com", 100L, null, "key-003");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void transfer_senderWalletInactive_throwsException() {
        senderWallet.setStatus("SUSPENDED");
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 100L, null, "key-004");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(InactiveWalletException.class)
                .hasMessageContaining("Sender");
    }

    @Test
    void transfer_recipientWalletInactive_throwsException() {
        recipientWallet.setStatus("CLOSED");
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 100L, null, "key-005");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(InactiveWalletException.class)
                .hasMessageContaining("Recipient");
    }

    @Test
    void transfer_currencyMismatch_throwsException() {
        recipientWallet.setCurrency("USD");
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 100L, null, "key-006");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void transfer_idempotencyReplay_returnsSameResultAfterFundsAreSpent() {
        senderWallet.setBalance(0L);
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 5000L, "Dinner", "key-007");

        Transfer existing = new Transfer("TRF-20261118-EXISTING", 1L, 2L, 5000L,
                "MYR", "Dinner", "key-007", OffsetDateTime.now());

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.findBySenderWalletIdAndIdempotencyKey(1L, "key-007"))
                .thenReturn(Optional.of(existing));

        TransferResponse response = transferService.transfer(10L, request);

        assertThat(response.transferReference()).isEqualTo("TRF-20261118-EXISTING");
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_idempotencyConflict_throwsExceptionWhenOnlyDescriptionChanges() {
        TransferRequest request = new TransferRequest(
                "recipient@example.com", 5000L, "Dinner", "key-008");

        Transfer existing = new Transfer("TRF-20261118-EXISTING", 1L, 2L, 5000L,
                "MYR", "Original description", "key-008", OffsetDateTime.now());

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipientUser));
        when(walletRepository.findIdByUserId(20L)).thenReturn(Optional.of(2L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.findBySenderWalletIdAndIdempotencyKey(1L, "key-008"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void transfer_recipientNotFound_throwsException() {
        TransferRequest request = new TransferRequest(
                "ghost@example.com", 100L, null, "key-009");

        when(walletRepository.findIdByUserId(10L)).thenReturn(Optional.of(1L));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(10L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reverse_locksAscendingBeforeCheckingReplayAndPostsOppositeDirection() {
        Transfer original = originalForReversal();
        when(transferRepository.findByReference("original")).thenReturn(Optional.of(original));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer posted = invocation.getArgument(0);
            ReflectionTestUtils.setField(posted, "id", 101L);
            return posted;
        });

        TransferResponse response = transferService.reverse("original");

        var order = inOrder(walletRepository, transferRepository, ledgerEntryRepository);
        order.verify(walletRepository).findByIdWithLock(1L);
        order.verify(walletRepository).findByIdWithLock(2L);
        order.verify(transferRepository).findByReversalOfTransferId(100L);
        ArgumentCaptor<Transfer> posted = ArgumentCaptor.forClass(Transfer.class);
        order.verify(transferRepository).saveAndFlush(posted.capture());
        assertThat(posted.getValue().getReversalOfTransferId()).isEqualTo(100L);
        assertThat(posted.getValue().getIdempotencyKey()).isNull();
        assertThat(response.senderWalletId()).isEqualTo(2L);
        assertThat(response.recipientWalletId()).isEqualTo(1L);
        assertThat(response.amount()).isEqualTo(5000L);
        assertThat(senderWallet.getBalance()).isEqualTo(15000L);
        assertThat(recipientWallet.getBalance()).isZero();

        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        order.verify(ledgerEntryRepository, times(2)).save(entries.capture());
        assertThat(entries.getAllValues())
                .extracting(LedgerEntry::getEntryType, LedgerEntry::getWalletId, LedgerEntry::getAmount)
                .containsExactly(tuple("DEBIT", 2L, 5000L), tuple("CREDIT", 1L, 5000L));
    }

    @Test
    void reverse_replaysBeforeCheckingFundsAndRejectsReversingTheReversal() {
        Transfer original = originalForReversal();
        Transfer reversal = Transfer.reversalOf("reversal", original, OffsetDateTime.now());
        recipientWallet.setBalance(0L);
        when(transferRepository.findByReference("original")).thenReturn(Optional.of(original));
        when(transferRepository.findByReference("reversal")).thenReturn(Optional.of(reversal));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.findByReversalOfTransferId(100L)).thenReturn(Optional.of(reversal));

        assertThat(transferService.reverse("original").transferReference()).isEqualTo("reversal");
        assertThatThrownBy(() -> transferService.reverse("reversal"))
                .isInstanceOf(TransferNotReversibleException.class);
        verify(transferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void reverse_insufficientFundsLeavesBothWalletsAndLedgerUntouched() {
        Transfer original = originalForReversal();
        recipientWallet.setBalance(1000L);
        when(transferRepository.findByReference("original")).thenReturn(Optional.of(original));
        when(walletRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> transferService.reverse("original"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(senderWallet.getBalance()).isEqualTo(10000L);
        assertThat(recipientWallet.getBalance()).isEqualTo(1000L);
        verify(transferRepository, never()).saveAndFlush(any());
        verifyNoInteractions(ledgerEntryRepository);
    }

    private Transfer originalForReversal() {
        Transfer original = new Transfer("original", 1L, 2L, 5000L, "MYR", null, "original-key",
                OffsetDateTime.now());
        ReflectionTestUtils.setField(original, "id", 100L);
        return original;
    }

}
