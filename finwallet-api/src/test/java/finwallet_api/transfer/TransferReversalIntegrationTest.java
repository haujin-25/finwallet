package finwallet_api.transfer;

import finwallet_api.common.exception.CurrencyMismatchException;
import finwallet_api.common.exception.InactiveWalletException;
import finwallet_api.common.exception.InsufficientFundsException;
import finwallet_api.common.exception.ResourceNotFoundException;
import finwallet_api.common.security.JwtService;
import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy;
import finwallet_api.reconciliation.service.LedgerReconciliationService;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.dto.TransferResponse;
import finwallet_api.transfer.entity.LedgerEntry;
import finwallet_api.transfer.entity.Transfer;
import finwallet_api.transfer.repository.LedgerEntryRepository;
import finwallet_api.transfer.repository.TransferRepository;
import finwallet_api.transfer.service.TransferNotReversibleException;
import finwallet_api.transfer.service.TransferService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferReversalIntegrationTest {

    @Autowired private TransferService service;
    @Autowired private TransferRepository transfers;
    @Autowired private LedgerReconciliationService reconciliation;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;

    @MockitoSpyBean
    private LedgerEntryRepository ledger;

    private Account alice;
    private Account bob;
    private Account carol;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> {
            alice = account("alice", 5000L);
            bob = account("bob", 0L);
            carol = account("carol", 0L);
        });
    }

    @AfterEach
    void cleanUp() {
        if (carol == null) {
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
    void fullReversalRestoresBalancesAndPreservesOriginalHistory() throws Exception {
        var baseline = reconciliation.reconcile();
        TransferResponse original = send(alice, bob, 5000L, "original");
        Transfer originalRow = find(original);
        List<Long> originalEntries = ledger.findByTransferId(originalRow.getId()).stream().map(LedgerEntry::getId).toList();

        TransferResponse reversal = service.reverse(original.transferReference());
        Transfer reversalRow = find(reversal);
        assertThat(reversal.transferReference()).isNotEqualTo(original.transferReference());
        assertThat(reversalRow.getReversalOfTransferId()).isEqualTo(originalRow.getId());
        assertThat(reversalRow.getIdempotencyKey()).isNull();
        assertThat(reversal.amount()).isEqualTo(original.amount());
        assertThat(reversal.currency()).isEqualTo(original.currency());
        assertThat(reversal.status()).isEqualTo("COMPLETED");
        assertThat(find(original)).usingRecursiveComparison().isEqualTo(originalRow);
        assertThat(ledger.findByTransferId(originalRow.getId())).extracting(LedgerEntry::getId)
                .containsExactlyInAnyOrderElementsOf(originalEntries);
        assertThat(ledger.findByTransferId(reversalRow.getId()))
                .extracting(LedgerEntry::getEntryType, LedgerEntry::getWalletId,
                        LedgerEntry::getAmount, LedgerEntry::getCurrency)
                .containsExactlyInAnyOrder(
                        tuple("DEBIT", bob.walletId(), 5000L, "MYR"),
                        tuple("CREDIT", alice.walletId(), 5000L, "MYR"));
        assertState(5000L, 0L, 0L, 2, 4);
        assertThat(service.reverse(original.transferReference())).isEqualTo(reversal);
        assertState(5000L, 0L, 0L, 2, 4);

        mvc.perform(get("/api/transfers/me").header("Authorization", authorization(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transferReference").value(reversal.transferReference()))
                .andExpect(jsonPath("$.items[0].operationType").value("REVERSAL"))
                .andExpect(jsonPath("$.items[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.items[0].reversalOfReference").value(original.transferReference()))
                .andExpect(jsonPath("$.items[1].operationType").value("TRANSFER"));

        // Remove the only non-ledger movement, the temporary fixture balance.
        jdbc.update("UPDATE wallets SET balance = 0 WHERE id = ?", alice.walletId());
        var report = reconciliation.reconcile();
        assertThat(report.discrepancies()).containsExactlyElementsOf(baseline.discrepancies().stream()
                .filter(issue -> !alice.walletId().equals(issue.walletId())).toList());
        assertThat(report.transfersChecked()).isEqualTo(baseline.transfersChecked() + 2);
    }

    @Test
    void reversalFailsWhenRecipientHasSpentFundsAndCanBeRetriedAfterTheyReturn() {
        TransferResponse original = send(alice, bob, 5000L, "original");
        send(bob, carol, 4000L, "spend");
        assertThatThrownBy(() -> service.reverse(original.transferReference()))
                .isInstanceOf(InsufficientFundsException.class);
        assertState(0L, 1000L, 4000L, 2, 4);

        send(carol, bob, 4000L, "return");
        service.reverse(original.transferReference());
        assertState(5000L, 0L, 0L, 4, 8);
    }

    @Test
    void rejectsMissingFailedAndReversalReferences() {
        assertThatThrownBy(() -> service.reverse("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        var original = send(alice, bob, 5000L, "original");
        var reversal = service.reverse(original.transferReference());
        assertThatThrownBy(() -> service.reverse(reversal.transferReference()))
                .isInstanceOf(TransferNotReversibleException.class);
        // The legacy status is still supported by the schema.
        jdbc.update("""
                INSERT INTO transfers (reference, sender_wallet_id, recipient_wallet_id, amount, currency,
                                       status, idempotency_key)
                VALUES ('reversal-legacy-failed', ?, ?, 100, 'MYR', 'FAILED', 'legacy')
                """, alice.walletId(), bob.walletId());
        assertThatThrownBy(() -> service.reverse("reversal-legacy-failed"))
                .isInstanceOf(TransferNotReversibleException.class);
        assertState(5000L, 0L, 0L, 3, 4);
    }

    @Test
    void enforcesWalletStatusOriginalCurrencyAndOverflow() {
        var original = send(alice, bob, 5000L, "original");
        jdbc.update("UPDATE wallets SET status = 'SUSPENDED' WHERE id = ?", bob.walletId());
        assertThatThrownBy(() -> service.reverse(original.transferReference()))
                .isInstanceOf(InactiveWalletException.class);
        jdbc.update("UPDATE wallets SET status = 'ACTIVE', currency = 'USD' WHERE id IN (?, ?)",
                alice.walletId(), bob.walletId());
        assertThatThrownBy(() -> service.reverse(original.transferReference()))
                .isInstanceOf(CurrencyMismatchException.class);
        jdbc.update("UPDATE wallets SET currency = 'MYR' WHERE id IN (?, ?)", alice.walletId(), bob.walletId());
        jdbc.update("UPDATE wallets SET balance = ? WHERE id = ?", Long.MAX_VALUE, alice.walletId());
        assertThatThrownBy(() -> service.reverse(original.transferReference()))
                .isInstanceOf(ArithmeticException.class);
        assertState(Long.MAX_VALUE, 5000L, 0L, 1, 2);
    }

    @Test
    void concurrentReversalsAndOppositeTransferFinishWithOneReversal() throws Exception {
        var original = send(alice, bob, 1000L, "original");
        var ready = new CountDownLatch(3);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.reverse(original.transferReference());
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return service.reverse(original.transferReference());
            });
            var opposite = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(alice, bob, 500L, "opposite");
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(opposite.get(10, TimeUnit.SECONDS).amount()).isEqualTo(500L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertState(4500L, 500L, 0L, 3, 6);
    }

    @Test
    void uniqueOriginalConstraintRejectsSecondReversal() {
        var original = send(alice, bob, 5000L, "original");
        service.reverse(original.transferReference());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfers (reference, sender_wallet_id, recipient_wallet_id, amount, currency,
                                       reversal_of_transfer_id)
                VALUES ('reversal-test-duplicate', ?, ?, 5000, 'MYR', ?)
                """, bob.walletId(), alice.walletId(), find(original).getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_transfers_reversal_original");
        assertState(5000L, 0L, 0L, 2, 4);
    }

    @Test
    void reconciliationDetectsInvalidReversalRelationship() {
        var original = send(alice, bob, 5000L, "original");
        long reversalId = find(service.reverse(original.transferReference())).getId();
        jdbc.update("""
                UPDATE transfers SET sender_wallet_id = ?, recipient_wallet_id = ?, amount = 4900, currency = 'USD'
                WHERE id = ?
                """, alice.walletId(), bob.walletId(), reversalId);

        assertThat(reconciliation.reconcile().discrepancies())
                .filteredOn(issue -> Long.valueOf(reversalId).equals(issue.transferId()))
                .extracting(ReconciliationDiscrepancy::type)
                .contains(ReconciliationDiscrepancy.Type.REVERSAL_DIRECTION_MISMATCH,
                        ReconciliationDiscrepancy.Type.REVERSAL_AMOUNT_MISMATCH,
                        ReconciliationDiscrepancy.Type.REVERSAL_CURRENCY_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackTransferRecordBothBalancesAndFirstLedgerEntryOnDatabaseFailure(boolean reversal) throws Exception {
        var original = reversal ? send(alice, bob, 5000L, "original") : null;
        doAnswer(invocation -> {
            LedgerEntry credit = invocation.getArgument(0);
            // CREDIT is the second save. Flush the wallet changes and provoke a real
            // constraint failure by inserting the existing DEBIT a second time.
            ledger.flush();
            jdbc.update("""
                    INSERT INTO ledger_entries (transfer_id, wallet_id, entry_type, amount, currency)
                    SELECT transfer_id, wallet_id, entry_type, amount, currency FROM ledger_entries
                    WHERE transfer_id = ? AND entry_type = 'DEBIT'
                    """, credit.getTransferId());
            throw new AssertionError("The database accepted a duplicate debit");
        }).when(ledger).save(argThat(entry -> entry != null && "CREDIT".equals(entry.getEntryType())));

        try {
            if (reversal) {
                assertThatThrownBy(() -> service.reverse(original.transferReference()))
                        .isInstanceOf(DataIntegrityViolationException.class);
                assertState(0L, 5000L, 0L, 1, 2);
            } else {
                mvc.perform(post("/api/transfers").header("Authorization", authorization(alice))
                                .contentType(MediaType.APPLICATION_JSON).content(payload(bob, "retry")))
                        .andExpect(status().isInternalServerError())
                        .andExpect(content().string("Unable to complete the request"));
                assertState(5000L, 0L, 0L, 0, 0);
            }
        } finally {
            reset(ledger);
        }

        if (reversal) {
            service.reverse(original.transferReference());
            assertState(5000L, 0L, 0L, 2, 4);
        } else {
            send(alice, bob, 5000L, "retry");
            assertState(0L, 5000L, 0L, 1, 2);
        }
    }

    @Test
    void retryAfterDiscardedResponseReplaysOriginalAndPublicReversalIsUnavailable() throws Exception {
        mvc.perform(post("/api/transfers").header("Authorization", authorization(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(bob, "lost-response")))
                .andExpect(status().isCreated());
        Transfer original = transfers.findBySenderWalletIdAndIdempotencyKey(alice.walletId(), "lost-response")
                .orElseThrow();
        mvc.perform(post("/api/transfers").header("Authorization", authorization(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(bob, "lost-response")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferReference").value(original.getReference()));
        mvc.perform(post("/api/transfers/{reference}/reverse", original.getReference())
                        .header("Authorization", authorization(alice)))
                .andExpect(status().isNotFound());
        assertState(0L, 5000L, 0L, 1, 2);
    }

    private void assertState(long aliceBalance, long bobBalance, long carolBalance, long transferCount, long entryCount) {
        assertThat(balance(alice)).isEqualTo(aliceBalance);
        assertThat(balance(bob)).isEqualTo(bobBalance);
        assertThat(balance(carol)).isEqualTo(carolBalance);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM transfers WHERE sender_wallet_id IN (?, ?, ?)
                """, Long.class, alice.walletId(), bob.walletId(), carol.walletId())).isEqualTo(transferCount);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_entries WHERE wallet_id IN (?, ?, ?)
                """, Long.class, alice.walletId(), bob.walletId(), carol.walletId())).isEqualTo(entryCount);
    }

    private long balance(Account account) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE id = ?", Long.class, account.walletId());
    }

    private Transfer find(TransferResponse result) {
        return transfers.findByReference(result.transferReference()).orElseThrow();
    }

    private TransferResponse send(Account sender, Account recipient, long amount, String key) {
        return service.transfer(sender.userId(), new TransferRequest(recipient.email(), amount, null, key));
    }

    private String authorization(Account account) {
        return "Bearer " + jwt.generateToken(account.email(), account.userId());
    }

    private String payload(Account recipient, String key) {
        return """
                {"recipientEmail":"%s","amount":5000,"idempotencyKey":"%s"}
                """.formatted(recipient.email(), key);
    }

    private Account account(String name, long openingBalance) {
        String email = "reversal-" + name + "@finwallet.test";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES (?, 'unused', ?) RETURNING id
                """, Long.class, email, name);
        Long walletId = jdbc.queryForObject("INSERT INTO wallets (user_id, balance) VALUES (?, ?) RETURNING id",
                Long.class, userId, openingBalance);
        return new Account(userId, walletId, email);
    }

    private record Account(Long userId, Long walletId, String email) {
    }
}
