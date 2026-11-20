package finwallet_api.reconciliation;

import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy;
import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy.Type;
import finwallet_api.reconciliation.dto.ReconciliationReport;
import finwallet_api.reconciliation.dto.ReconciliationReport.Status;
import finwallet_api.reconciliation.repository.ReconciliationRepository;
import finwallet_api.reconciliation.service.LedgerReconciliationService;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.repository.TransferRepository;
import finwallet_api.transfer.service.TransferService;
import finwallet_api.wallet.service.WalletService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class LedgerReconciliationIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WalletService walletService;
    @Autowired private TransferService transferService;
    @Autowired private TransferRepository transferRepository;
    @Autowired private LedgerReconciliationService reconciliation;
    @Autowired private TransactionTemplate transactions;

    @MockitoSpyBean
    private ReconciliationRepository reconciliationRepository;

    private ReconciliationReport baseline;
    private Account alice;
    private Account bob;
    private Account carol;

    @BeforeEach
    void setUp() {
        baseline = reconciliation.reconcile();
        transactions.executeWithoutResult(transaction -> {
            alice = account("alice");
            bob = account("bob");
            carol = account("carol");
        });
    }

    @AfterEach
    void tearDown() {
        if (alice == null || bob == null || carol == null) {
            return;
        }
        transactions.executeWithoutResult(transaction -> {
            jdbc.update("""
                    DELETE FROM ledger_entries WHERE transfer_id IN (
                        SELECT id FROM transfers WHERE sender_wallet_id IN (?, ?, ?)
                    )
                    """, alice.walletId(), bob.walletId(), carol.walletId());
            jdbc.update("DELETE FROM transfers WHERE sender_wallet_id IN (?, ?, ?)",
                    alice.walletId(), bob.walletId(), carol.walletId());
            jdbc.update("DELETE FROM wallets WHERE id IN (?, ?, ?)", alice.walletId(), bob.walletId(), carol.walletId());
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", alice.userId(), bob.userId(), carol.userId());
        });
    }

    @Test
    void zeroOpeningWalletsAndZeroTransfersReconcile() {
        ReconciliationReport report = reconciliation.reconcile();
        assertThat(report.reconciledAt()).isNotNull();
        assertThat(report.walletsChecked()).isEqualTo(baseline.walletsChecked() + 3);
        assertThat(report.transfersChecked()).isEqualTo(baseline.transfersChecked());
        assertThat(report.status()).isEqualTo(baseline.status());
        assertThat(report.discrepancies()).containsExactlyElementsOf(baseline.discrepancies());
    }

    @Test
    void validRoundTripReconcilesOnceTemporaryFixtureFundingIsRemoved() {
        completeRoundTrip();

        ReconciliationReport report = reconciliation.reconcile();
        assertThat(report.walletsChecked()).isEqualTo(baseline.walletsChecked() + 3);
        assertThat(report.transfersChecked()).isEqualTo(baseline.transfersChecked() + 2);
        assertThat(report.discrepancyCount()).isEqualTo(baseline.discrepancyCount());
        assertThat(report.discrepancies()).containsExactlyElementsOf(baseline.discrepancies());
        assertThat(report.status()).isEqualTo(baseline.status());
    }

    @Test
    void unrecordedFundingIsReportedInsteadOfInferredAsOpeningBalance() {
        balance(alice, 100);

        ReconciliationReport report = reconciliation.reconcile();
        assertThat(report.status()).isEqualTo(Status.FAIL);
        assertThat(report.discrepancies()).anySatisfy(discrepancy -> {
            assertThat(discrepancy.type()).isEqualTo(Type.BALANCE_MISMATCH);
            assertThat(discrepancy.walletId()).isEqualTo(alice.walletId());
            assertThat(discrepancy.detail()).contains("Stored 100", "expected 0");
        });
    }

    @ParameterizedTest
    @CsvSource({"DEBIT,MISSING_DEBIT", "CREDIT,MISSING_CREDIT"})
    void detectsMissingLedgerEntry(String entryType, Type expectedType) {
        long transferId = completeRoundTrip();
        jdbc.update("DELETE FROM ledger_entries WHERE transfer_id = ? AND entry_type = ?", transferId, entryType);

        ReconciliationReport report = reconciliation.reconcile();
        assertThat(report.status()).isEqualTo(Status.FAIL);
        assertThat(typesForTransfer(report, transferId)).containsExactly(expectedType);
    }

    @Test
    void detectsWrongWalletAmountCurrencyAndCorruptedBalance() {
        long transferId = completeRoundTrip();
        Long entryId = jdbc.queryForObject("""
                UPDATE ledger_entries SET wallet_id = ?, amount = amount + 1, currency = 'USD'
                WHERE transfer_id = ? AND entry_type = 'CREDIT' RETURNING id
                """, Long.class, carol.walletId(), transferId);
        balance(alice, 5);

        ReconciliationReport report = reconciliation.reconcile();
        assertThat(report.status()).isEqualTo(Status.FAIL);
        assertThat(report.discrepancies()).filteredOn(issue -> entryId.equals(issue.ledgerEntryId()))
                .extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(Type.WRONG_WALLET, Type.AMOUNT_MISMATCH, Type.CURRENCY_MISMATCH);
        assertThat(report.discrepancies()).anyMatch(issue ->
                issue.type() == Type.BALANCE_MISMATCH && alice.walletId().equals(issue.walletId()));
    }

    @Test
    void reportsFailedTransferEntriesWithoutCountingThemAsPosted() {
        long transferId = completeRoundTrip();
        jdbc.update("UPDATE transfers SET status = 'FAILED' WHERE id = ?", transferId);

        var report = reconciliation.reconcile();
        assertThat(typesForTransfer(report, transferId))
                .containsExactly(Type.UNEXPECTED_ENTRY, Type.UNEXPECTED_ENTRY);
        // Removing the first posting from the net leaves only the completed return leg pair.
        assertThat(report.discrepancies()).filteredOn(issue -> issue.type() == Type.BALANCE_MISMATCH)
                .extracting(ReconciliationDiscrepancy::walletId)
                .contains(alice.walletId(), bob.walletId());
    }

    @Test
    void balanceAggregationKeepsTotalsBeyondLongRangeExact() {
        long firstTransfer = completeRoundTrip();
        long secondTransfer = transferRepository.findBySenderWalletIdAndIdempotencyKey(bob.walletId(), "return")
                .orElseThrow().getId();
        jdbc.update("""
                UPDATE ledger_entries SET amount = ?, wallet_id = ?
                WHERE transfer_id IN (?, ?) AND entry_type = 'CREDIT'
                """, Long.MAX_VALUE, carol.walletId(), firstTransfer, secondTransfer);

        assertThat(reconciliation.reconcile().discrepancies()).anySatisfy(issue -> {
            assertThat(issue.type()).isEqualTo(Type.BALANCE_MISMATCH);
            assertThat(issue.walletId()).isEqualTo(carol.walletId());
            assertThat(issue.detail()).contains("18446744073709551614");
        });
    }

    @Test
    void usesTheSameSnapshotWhenATransferCommitsBetweenQueries() throws Exception {
        balance(alice, 1000);
        ReconciliationReport before = reconciliation.reconcile();

        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch transferCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object snapshot = invocation.callRealMethod();
            snapshotTaken.countDown();
            if (!transferCommitted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for transfer commit");
            }
            return snapshot;
        }).when(reconciliationRepository).countRecords();

        var worker = Executors.newSingleThreadExecutor();
        try {
            var runningReport = worker.submit(reconciliation::reconcile);
            assertThat(snapshotTaken.await(10, TimeUnit.SECONDS)).isTrue();
            send(alice, bob, "concurrent");
            transferCommitted.countDown();

            ReconciliationReport during = runningReport.get(10, TimeUnit.SECONDS);
            assertThat(during.transfersChecked()).isEqualTo(before.transfersChecked());
            assertThat(during.discrepancies()).containsExactlyElementsOf(before.discrepancies());

            ReconciliationReport after = reconciliation.reconcile();
            assertThat(after.transfersChecked()).isEqualTo(before.transfersChecked() + 1);
            assertThat(after.discrepancyCount()).isEqualTo(before.discrepancyCount());
            assertThat(after.discrepancies()).anySatisfy(issue -> {
                assertThat(issue.type()).isEqualTo(Type.BALANCE_MISMATCH);
                assertThat(issue.walletId()).isEqualTo(alice.walletId());
                assertThat(issue.detail()).contains("Stored 900", "expected -100");
            });
        } finally {
            transferCommitted.countDown();
            worker.shutdownNow();
        }
    }

    private long completeRoundTrip() {
        // Temporary test funds let the real service post two opposite transfers.
        balance(alice, 1000);
        long outboundId = send(alice, bob, "outbound");
        send(bob, alice, "return");
        balance(alice, 0);
        return outboundId;
    }

    private void balance(Account account, long amount) {
        jdbc.update("UPDATE wallets SET balance = ? WHERE id = ?", amount, account.walletId());
    }

    private long send(Account sender, Account recipient, String key) {
        var result = transferService.transfer(sender.userId(), new TransferRequest(recipient.email(), 100L, null, key));
        return transferRepository.findByReference(result.transferReference()).orElseThrow().getId();
    }

    private List<Type> typesForTransfer(ReconciliationReport report, long transferId) {
        return report.discrepancies().stream().filter(issue -> Long.valueOf(transferId).equals(issue.transferId()))
                .map(ReconciliationDiscrepancy::type).toList();
    }

    private Account account(String name) {
        String email = "reconciliation-" + name + "@finwallet.test";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES (?, 'unused', ?) RETURNING id
                """, Long.class, email, name);
        Long walletId = walletService.createWalletForUser(userId).getId();
        return new Account(userId, walletId, email);
    }

    private record Account(Long userId, Long walletId, String email) {
    }
}
