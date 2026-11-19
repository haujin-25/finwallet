package finwallet_api.funding;

import finwallet_api.funding.entity.FundingOperation;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.provider.SimulatedPaymentProvider;
import finwallet_api.funding.repository.FundingJournalRepository;
import finwallet_api.funding.repository.FundingOperationRepository;
import finwallet_api.funding.service.FundingSettlementService;
import finwallet_api.funding.service.FundingStateConflictException;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingSettlementServiceTest {

    @Mock private FundingOperationRepository operations;
    @Mock private WalletRepository wallets;
    @Mock private FundingJournalRepository journal;

    @Test
    void timeoutKeepsReservationAndFailureReleasesItOnlyOnce() {
        Wallet wallet = new Wallet();
        wallet.setBalance(1000L);
        wallet.reserve(800);
        var operation = pending(FundingType.WITHDRAWAL, 800, wallet);
        var settlement = settlement();

        assertThat(settlement.checkStatus(operation.getReference(), Scenario.TIMEOUT).status())
                .isEqualTo(FundingStatus.PENDING);
        assertThat(wallet.getAvailableBalance()).isEqualTo(200);
        assertThat(operation.getProviderEventReference()).isNull();
        var order = inOrder(operations, wallets);
        order.verify(operations).findWalletIdByReference(operation.getReference());
        order.verify(wallets).findByIdWithLock(25L);
        order.verify(operations).findByWalletIdAndReference(25L, operation.getReference());

        var failed = settlement.checkStatus(operation.getReference(), Scenario.FAILURE);
        assertThat(failed.status()).isEqualTo(FundingStatus.FAILED);
        assertThat(settlement.checkStatus(operation.getReference(), Scenario.FAILURE)).isEqualTo(failed);
        assertThatThrownBy(() -> settlement.checkStatus(operation.getReference(), Scenario.SUCCESS))
                .isInstanceOf(FundingStateConflictException.class);
        assertThat(wallet.getBalance()).isEqualTo(1000L);
        assertThat(wallet.getReservedBalance()).isZero();
        verifyNoInteractions(journal);
    }

    @Test
    void successfulDepositReplaysSameProviderEventAndAppendsOnlyOneJournal() {
        Wallet wallet = new Wallet();
        var operation = pending(FundingType.DEPOSIT, 800, wallet);
        var settlement = settlement();

        var result = settlement.checkStatus(operation.getReference(), Scenario.SUCCESS);
        assertThat(settlement.checkStatus(operation.getReference(), Scenario.SUCCESS)).isEqualTo(result);
        assertThat(wallet.getBalance()).isEqualTo(800L);
        assertThat(operation.getProviderEventReference()).isEqualTo(operation.getProviderReference() + ":SUCCESS");
        verify(journal).append(operation);
    }

    @Test
    void overflowLeavesDepositPendingAndCreatesNoJournal() {
        Wallet wallet = new Wallet();
        wallet.setBalance(Long.MAX_VALUE);
        var operation = pending(FundingType.DEPOSIT, 1, wallet);

        assertThatThrownBy(() -> settlement().checkStatus(operation.getReference(), Scenario.SUCCESS))
                .isInstanceOf(ArithmeticException.class);
        assertThat(wallet.getBalance()).isEqualTo(Long.MAX_VALUE);
        assertThat(operation.getStatus()).isEqualTo(FundingStatus.PENDING);
        verifyNoInteractions(journal);
    }

    private FundingSettlementService settlement() {
        return new FundingSettlementService(operations, wallets, journal, new SimulatedPaymentProvider());
    }

    private FundingOperation pending(FundingType type, long amount, Wallet wallet) {
        var operation = new FundingOperation(25L, type, amount, null, "key", OffsetDateTime.now());
        when(operations.findWalletIdByReference(operation.getReference())).thenReturn(Optional.of(25L));
        when(wallets.findByIdWithLock(25L)).thenReturn(Optional.of(wallet));
        when(operations.findByWalletIdAndReference(25L, operation.getReference())).thenReturn(Optional.of(operation));
        return operation;
    }
}
