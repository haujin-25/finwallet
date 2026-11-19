package finwallet_api.funding;

import com.jayway.jsonpath.JsonPath;
import finwallet_api.common.exception.InsufficientFundsException;
import finwallet_api.common.security.JwtService;
import finwallet_api.funding.dto.FundingRequest;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.repository.FundingJournalRepository;
import finwallet_api.funding.service.FundingService;
import finwallet_api.funding.service.FundingSettlementService;
import finwallet_api.funding.service.FundingStateConflictException;
import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy;
import finwallet_api.reconciliation.service.LedgerReconciliationService;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.service.TransferService;
import finwallet_api.wallet.repository.WalletRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finwallet.dev.funding.enabled=true",
        "finwallet.dev.funding.token=integration-test-dev-funding-token"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FundingIntegrationTest {

    @Autowired private FundingService funding;
    @Autowired private FundingSettlementService settlement;
    @Autowired private TransferService transfers;
    @Autowired private LedgerReconciliationService reconciliation;
    @Autowired private WalletRepository wallets;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;

    @MockitoSpyBean
    private FundingJournalRepository journal;

    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> {
            alice = account("alice");
            bob = account("bob");
        });
    }

    @AfterEach
    void cleanUp() {
        if (bob == null) {
            return;
        }
        cleanUpAccounts(alice, bob);
    }

    private void cleanUpAccounts(Account alice, Account bob) {
        transactions.executeWithoutResult(transaction -> {
            jdbc.update("""
                    DELETE FROM funding_ledger_entries WHERE operation_id IN (
                        SELECT id FROM funding_operations WHERE wallet_id IN (?, ?))
                    """, alice.walletId(), bob.walletId());
            jdbc.update("DELETE FROM funding_operations WHERE wallet_id IN (?, ?)", alice.walletId(), bob.walletId());
            jdbc.update("""
                    DELETE FROM ledger_entries WHERE transfer_id IN (
                        SELECT id FROM transfers WHERE sender_wallet_id IN (?, ?))
                    """, alice.walletId(), bob.walletId());
            jdbc.update("DELETE FROM transfers WHERE sender_wallet_id IN (?, ?)", alice.walletId(), bob.walletId());
            jdbc.update("DELETE FROM wallets WHERE id IN (?, ?)", alice.walletId(), bob.walletId());
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", alice.userId(), bob.userId());
        });
    }

    @Test
    void depositRequestIsUncreditedAndScopedToAuthenticatedWallet() throws Exception {
        mvc.perform(post("/api/deposits").contentType(MediaType.APPLICATION_JSON).content(payload(500, "key")))
                .andExpect(status().isUnauthorized());

        String body = mvc.perform(post("/api/deposits").header("Authorization", authorization(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(500, "key")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerReference").doesNotHaveJsonPath())
                .andReturn().getResponse().getContentAsString();

        var original = request(alice, FundingType.DEPOSIT, 500, "key");
        assertThat(body).contains(original.reference());
        assertWallet(alice, 0, 0);
        assertThat(entries(original)).isEmpty();

        mvc.perform(get("/api/funding/{reference}", original.reference())
                        .header("Authorization", authorization(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/deposits").header("Authorization", authorization(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500,"idempotencyKey":"attempt","walletId":1,"status":"COMPLETED"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/funding/{reference}/confirm", original.reference())
                        .header("Authorization", authorization(alice)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/deposits").header("Authorization", authorization(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(501, "key")))
                .andExpect(status().isConflict());

        var otherOwner = request(bob, FundingType.DEPOSIT, 500, "key");
        assertThat(otherOwner.reference()).isNotEqualTo(original.reference());
        assertWallet(bob, 0, 0);
    }

    @Test
    void devEndpointSettlesAndReplaysWithoutCreditingTwice() throws Exception {
        var deposit = request(alice, FundingType.DEPOSIT, 10000, "dev-success");
        assertThat(settleViaHttp(deposit, "SUCCESS")).isEqualTo(settleViaHttp(deposit, "SUCCESS"));
        assertWallet(alice, 10000, 0);
        assertThat(entries(deposit)).containsExactly(
                new Entry("CREDIT", alice.walletId(), null, 10000, "MYR"),
                new Entry("DEBIT", null, "SIMULATED_MYR_SETTLEMENT", 10000, "MYR"));
    }

    @Test
    void devEndpointCanFailADepositWithoutPostingEntries() throws Exception {
        var deposit = request(alice, FundingType.DEPOSIT, 10000, "dev-failure");
        settleViaHttp(deposit, "FAILURE");
        mvc.perform(post("/api/dev/funding/{reference}/settle", deposit.reference())
                        .header("X-Dev-Funding-Token", "integration-test-dev-funding-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isConflict());
        assertWallet(alice, 0, 0);
        assertThat(entries(deposit)).isEmpty();
    }

    @Test
    void concurrentDevRequestsCreditOnlyOnce() throws Exception {
        var deposit = request(alice, FundingType.DEPOSIT, 10000, "dev-concurrent");
        var results = concurrently(() -> settleViaHttp(deposit, "SUCCESS"), () -> settleViaHttp(deposit, "SUCCESS"));
        assertThat(results).hasSize(2).allMatch(results.getFirst()::equals);
        assertWallet(alice, 10000, 0);
        assertThat(entries(deposit)).hasSize(2);
    }

    @Test
    void customerJwtAloneCannotSettleADeposit() throws Exception {
        var deposit = request(alice, FundingType.DEPOSIT, 10000, "dev-unauthorized");
        mvc.perform(post("/api/dev/funding/{reference}/settle", deposit.reference())
                        .header("Authorization", authorization(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());
        assertThat(funding.get(alice.userId(), deposit.reference()).status()).isEqualTo(FundingStatus.PENDING);
        assertWallet(alice, 0, 0);
        assertThat(entries(deposit)).isEmpty();
    }

    private String settleViaHttp(FundingResponse deposit, String outcome) throws Exception {
        return mvc.perform(post("/api/dev/funding/{reference}/settle", deposit.reference())
                        .header("X-Dev-Funding-Token", "integration-test-dev-funding-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"%s\"}".formatted(outcome)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(deposit.reference()))
                .andExpect(jsonPath("$.status").value("SUCCESS".equals(outcome) ? "COMPLETED" : "FAILED"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void registeredUsersCanSettleRm100AndReplayAnRm1050Transfer() throws Exception {
        var sender = registerWorkflowAccount();
        Account recipient = null;
        try {
            recipient = registerWorkflowAccount();
            String senderAuth = loginWorkflowAccount(sender);
            String depositBody = mvc.perform(post("/api/deposits")
                            .header("Authorization", senderAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload(10000, "workflow-deposit")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn().getResponse().getContentAsString();
            String depositReference = JsonPath.read(depositBody, "$.reference");
            assertWallet(sender, 0, 0);

            var deposit = funding.get(sender.userId(), depositReference);
            settleViaHttp(deposit, "SUCCESS");
            assertWallet(sender, 10000, 0);
            assertThat(entries(deposit)).containsExactly(
                    new Entry("CREDIT", sender.walletId(), null, 10000, "MYR"),
                    new Entry("DEBIT", null, "SIMULATED_MYR_SETTLEMENT", 10000, "MYR"));

            String transferPayload = """
                    {"recipientEmail":"%s","amount":1050,"idempotencyKey":"workflow-transfer"}
                    """.formatted(recipient.email());
            String reference = postWorkflowTransfer(senderAuth, transferPayload);
            assertThat(postWorkflowTransfer(senderAuth, transferPayload)).isEqualTo(reference);
            assertWallet(sender, 8950, 0);
            assertWallet(recipient, 1050, 0);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM transfers
                    WHERE sender_wallet_id = ? AND idempotency_key = 'workflow-transfer'
                    """, Long.class, sender.walletId())).isEqualTo(1L);
            assertThat(jdbc.query("""
                    SELECT e.entry_type, e.wallet_id, e.amount, e.currency
                    FROM ledger_entries e JOIN transfers t ON t.id = e.transfer_id
                    WHERE t.reference = ?
                    """, (row, index) -> new Entry(row.getString("entry_type"), row.getLong("wallet_id"),
                    null, row.getLong("amount"), row.getString("currency")), reference))
                    .containsExactlyInAnyOrder(
                            new Entry("DEBIT", sender.walletId(), null, 1050, "MYR"),
                            new Entry("CREDIT", recipient.walletId(), null, 1050, "MYR"));
        } finally {
            cleanUpAccounts(sender, recipient == null ? sender : recipient);
        }
    }

    private static final String WORKFLOW_PASSWORD = "Local-workflow-test-123";

    private Account registerWorkflowAccount() throws Exception {
        String email = "workflow-" + UUID.randomUUID() + "@finwallet.test";
        String body = mvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Workflow Test"}
                                """.formatted(email, WORKFLOW_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = ((Number) JsonPath.read(body, "$.id")).longValue();
        return new Account(userId, wallets.findIdByUserId(userId).orElseThrow(), email);
    }

    private String loginWorkflowAccount(Account account) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(account.email(), WORKFLOW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(body, "$.accessToken");
    }

    private String postWorkflowTransfer(String senderAuth, String payload) throws Exception {
        String body = mvc.perform(post("/api/transfers")
                        .header("Authorization", senderAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1050))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.transferReference");
    }

    @Test
    void depositConfirmsOnceAndPersistsProviderReferencesAndBalancedEntries() {
        var deposit = request(alice, FundingType.DEPOSIT, 1000, "deposit");
        var completed = settlement.checkStatus(deposit.reference(), Scenario.SUCCESS);
        assertThat(completed.status()).isEqualTo(FundingStatus.COMPLETED);
        assertThat(settlement.checkStatus(deposit.reference(), Scenario.SUCCESS)).isEqualTo(completed);
        assertThat(request(alice, FundingType.DEPOSIT, 1000, "deposit")).isEqualTo(completed);
        assertThatThrownBy(() -> settlement.checkStatus(deposit.reference(), Scenario.FAILURE))
                .isInstanceOf(FundingStateConflictException.class);
        assertWallet(alice, 1000, 0);
        assertThat(entries(deposit)).containsExactly(
                new Entry("CREDIT", alice.walletId(), null, 1000, "MYR"),
                new Entry("DEBIT", null, "SIMULATED_MYR_SETTLEMENT", 1000, "MYR"));
        assertThat(jdbc.queryForObject("""
                SELECT provider_event_reference = provider_reference || ':SUCCESS'
                FROM funding_operations WHERE reference = ?
                """, Boolean.class, deposit.reference())).isTrue();
    }

    @Test
    void concurrentDepositConfirmationReturnsOneResult() throws Exception {
        var deposit = request(alice, FundingType.DEPOSIT, 1000, "deposit");
        Callable<FundingResponse> confirm = () -> settlement.checkStatus(deposit.reference(), Scenario.SUCCESS);
        var results = concurrently(confirm, confirm);
        assertThat(results).hasSize(2).allMatch(results.getFirst()::equals);
        assertWallet(alice, 1000, 0);
        assertThat(entries(deposit)).hasSize(2);
    }

    @Test
    void failedDepositIsUncreditedAndCannotLaterSucceed() {
        var deposit = request(alice, FundingType.DEPOSIT, 1000, "deposit");
        assertThat(settlement.checkStatus(deposit.reference(), Scenario.FAILURE).status()).isEqualTo(FundingStatus.FAILED);
        assertThatThrownBy(() -> settlement.checkStatus(deposit.reference(), Scenario.SUCCESS))
                .isInstanceOf(FundingStateConflictException.class);
        assertWallet(alice, 0, 0);
        assertThat(entries(deposit)).isEmpty();
    }

    @Test
    void payoutTimeoutKeepsHoldAndSuccessDebitsExactlyOnce() throws Exception {
        fund(alice, 1000);
        // The deposit and withdrawal can use the same key for the same wallet.
        var withdrawal = request(alice, FundingType.WITHDRAWAL, 750, "seed");
        assertWallet(alice, 1000, 750);
        assertThat(entries(withdrawal)).isEmpty();
        assertThat(settlement.checkStatus(withdrawal.reference(), Scenario.TIMEOUT)).isEqualTo(withdrawal);
        assertThat(request(alice, FundingType.WITHDRAWAL, 750, "seed")).isEqualTo(withdrawal);
        assertWallet(alice, 1000, 750);

        mvc.perform(get("/api/wallets/me").header("Authorization", authorization(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000))
                .andExpect(jsonPath("$.availableBalance").value(250))
                .andExpect(jsonPath("$.reservedBalance").value(750));
        assertThatThrownBy(() -> request(alice, FundingType.WITHDRAWAL, 251, "too-much"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> transfers.transfer(alice.userId(), new TransferRequest(bob.email(), 251L, null, "too-much")))
                .isInstanceOf(InsufficientFundsException.class);

        var result = settlement.checkStatus(withdrawal.reference(), Scenario.SUCCESS);
        assertThat(result.status()).isEqualTo(FundingStatus.COMPLETED);
        assertThat(settlement.checkStatus(withdrawal.reference(), Scenario.SUCCESS)).isEqualTo(result);
        assertWallet(alice, 250, 0);
        assertThat(entries(withdrawal)).containsExactly(
                new Entry("DEBIT", alice.walletId(), null, 750, "MYR"),
                new Entry("CREDIT", null, "SIMULATED_MYR_SETTLEMENT", 750, "MYR"));
    }

    @Test
    void failedPayoutReleasesTheHoldWithoutJournalEntries() {
        fund(alice, 1000);
        var withdrawal = request(alice, FundingType.WITHDRAWAL, 750, "payout");
        var failed = settlement.checkStatus(withdrawal.reference(), Scenario.FAILURE);
        assertThat(failed.status()).isEqualTo(FundingStatus.FAILED);
        assertThat(settlement.checkStatus(withdrawal.reference(), Scenario.FAILURE)).isEqualTo(failed);
        assertWallet(alice, 1000, 0);
        assertThat(entries(withdrawal)).isEmpty();
    }

    @Test
    void concurrentWithdrawalsCannotOverReserveAndCompetingResultsCannotBothWin() throws Exception {
        fund(alice, 1000);
        var reservations = concurrently(() -> tryReserve("first"), () -> tryReserve("second"));
        assertThat(reservations).containsExactlyInAnyOrder("reserved", "insufficient");
        assertWallet(alice, 1000, 750);

        String reference = jdbc.queryForObject("""
                SELECT reference FROM funding_operations WHERE wallet_id = ? AND operation_type = 'WITHDRAWAL'
                """, String.class, alice.walletId());
        var endings = concurrently(() -> tryResolve(reference, Scenario.SUCCESS),
                () -> tryResolve(reference, Scenario.FAILURE));
        assertThat(endings.stream().filter("conflict"::equals)).hasSize(1);
        var payout = funding.get(alice.userId(), reference);
        if (payout.status() == FundingStatus.COMPLETED) {
            assertWallet(alice, 250, 0);
            assertThat(entries(payout)).hasSize(2);
        } else {
            assertThat(payout.status()).isEqualTo(FundingStatus.FAILED);
            assertWallet(alice, 1000, 0);
            assertThat(entries(payout)).isEmpty();
        }
    }

    @Test
    void activityShowsAllOperationTypesAndReconciliationIncludesFundingAndHolds() throws Exception {
        var baseline = reconciliation.reconcile().discrepancies();
        fund(alice, 1000);
        var original = transfers.transfer(alice.userId(), new TransferRequest(bob.email(), 300L, null, "transfer"));
        var hold = request(bob, FundingType.WITHDRAWAL, 100, "hold");
        assertThatThrownBy(() -> transfers.reverse(original.transferReference()))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(reconciliation.reconcile().discrepancies()).containsExactlyElementsOf(baseline);

        settlement.checkStatus(hold.reference(), Scenario.FAILURE);
        transfers.reverse(original.transferReference());
        var payout = request(alice, FundingType.WITHDRAWAL, 500, "activity");
        settlement.checkStatus(payout.reference(), Scenario.SUCCESS);
        assertWallet(alice, 500, 0);
        assertWallet(bob, 0, 0);
        assertThat(reconciliation.reconcile().discrepancies()).containsExactlyElementsOf(baseline);

        mvc.perform(get("/api/wallets/me/activity").header("Authorization", authorization(alice))
                        .param("walletId", bob.walletId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].operationType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[1].operationType").value("REVERSAL"))
                .andExpect(jsonPath("$.items[2].operationType").value("TRANSFER"))
                .andExpect(jsonPath("$.items[3].operationType").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[0].providerReference").doesNotHaveJsonPath());
        mvc.perform(get("/api/wallets/me/activity").header("Authorization", authorization(bob)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].operationType").value("REVERSAL"));
    }

    @Test
    void reconciliationDetectsIncorrectSystemEntriesAndMissingHolds() {
        var deposit = fund(alice, 1000);
        request(alice, FundingType.WITHDRAWAL, 200, "hold");
        jdbc.update("UPDATE wallets SET reserved_balance = 0 WHERE id = ?", alice.walletId());
        jdbc.update("""
                UPDATE funding_ledger_entries SET amount = amount + 1
                WHERE account_code IS NOT NULL AND operation_id =
                    (SELECT id FROM funding_operations WHERE reference = ?)
                """, deposit.reference());
        assertThat(reconciliation.reconcile().discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .contains(ReconciliationDiscrepancy.Type.RESERVATION_MISMATCH,
                        ReconciliationDiscrepancy.Type.FUNDING_ENTRY_MISMATCH,
                        ReconciliationDiscrepancy.Type.SETTLEMENT_ACCOUNT_MISMATCH);
    }

    @Test
    void databaseGuardsProviderEventAndJournalUniquenessAndReconciliationDetectsMissingLeg() {
        var first = fund(alice, 1000);
        var second = fund(bob, 1000);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE funding_operations SET provider_event_reference =
                    (SELECT provider_event_reference FROM funding_operations WHERE reference = ?)
                WHERE reference = ?
                """, first.reference(), second.reference()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO funding_ledger_entries
                    (operation_id, wallet_id, account_code, entry_type, amount, currency, created_at)
                SELECT e.operation_id, e.wallet_id, e.account_code, e.entry_type, e.amount, e.currency, e.created_at
                FROM funding_ledger_entries e JOIN funding_operations f ON f.id = e.operation_id
                WHERE f.reference = ? AND e.entry_type = 'DEBIT'
                """, first.reference()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_funding_operation_entry_type");

        jdbc.update("""
                DELETE FROM funding_ledger_entries WHERE entry_type = 'DEBIT'
                AND operation_id = (SELECT id FROM funding_operations WHERE reference = ?)
                """, first.reference());
        assertThat(reconciliation.reconcile().discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .contains(ReconciliationDiscrepancy.Type.FUNDING_OPERATION_MISMATCH,
                        ReconciliationDiscrepancy.Type.SETTLEMENT_ACCOUNT_MISMATCH);
    }

    @ParameterizedTest
    @EnumSource(FundingType.class)
    void failureAfterJournalInsertRollsBackWalletStateAndBothEntries(FundingType type) {
        boolean withdrawal = type == FundingType.WITHDRAWAL;
        if (withdrawal) {
            fund(alice, 1000);
        }
        var pending = request(alice, type, 500, "rollback");
        // Stub the spy behind the proxy. The service still calls the transactional journal proxy.
        FundingJournalRepository journalSpy = AopTestUtils.getUltimateTargetObject(journal);
        try {
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                wallets.flush();
                invocation.callRealMethod();

                assertThat(jdbc.queryForObject("""
                        SELECT status FROM funding_operations WHERE reference = ?
                        """, String.class, pending.reference())).isEqualTo("COMPLETED");
                assertThat(jdbc.queryForList("""
                        SELECT balance, reserved_balance FROM wallets WHERE id = ?
                        """, alice.walletId())).singleElement().satisfies(row -> {
                            assertThat(row).containsEntry("balance", 500L).containsEntry("reserved_balance", 0L);
                        });
                assertThat(entries(pending)).hasSize(2);
                throw new DataIntegrityViolationException("Injected post-journal failure");
            }).when(journalSpy).append(any());

            assertThatThrownBy(() -> settlement.checkStatus(pending.reference(), Scenario.SUCCESS))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Injected");
            assertThat(funding.get(alice.userId(), pending.reference())).isEqualTo(pending);
            assertWallet(alice, withdrawal ? 1000 : 0, withdrawal ? 500 : 0);
            assertThat(entries(pending)).isEmpty();
        } finally {
            reset(journalSpy);
        }
        assertThat(settlement.checkStatus(pending.reference(), Scenario.SUCCESS).status())
                .isEqualTo(FundingStatus.COMPLETED);
        assertWallet(alice, 500, 0);
        assertThat(entries(pending)).hasSize(2);
    }

    private String tryReserve(String key) {
        try {
            request(alice, FundingType.WITHDRAWAL, 750, key);
            return "reserved";
        } catch (InsufficientFundsException expected) {
            return "insufficient";
        }
    }

    private String tryResolve(String reference, Scenario scenario) {
        try {
            return settlement.checkStatus(reference, scenario).status().name();
        } catch (FundingStateConflictException expected) {
            return "conflict";
        }
    }

    private <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var one = executor.submit(() -> {
                ready.countDown();
                start.await();
                return first.call();
            });
            var two = executor.submit(() -> {
                ready.countDown();
                start.await();
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private FundingResponse fund(Account account, long amount) {
        var deposit = request(account, FundingType.DEPOSIT, amount, "seed");
        return settlement.checkStatus(deposit.reference(), Scenario.SUCCESS);
    }

    private FundingResponse request(Account account, FundingType type, long amount, String key) {
        return funding.request(account.userId(), type, new FundingRequest(amount, key, null));
    }

    private List<Entry> entries(FundingResponse operation) {
        return jdbc.query("""
                SELECT e.entry_type, e.wallet_id, e.account_code, e.amount, e.currency
                FROM funding_ledger_entries e JOIN funding_operations f ON f.id = e.operation_id
                WHERE f.reference = ? ORDER BY e.wallet_id NULLS LAST
                """, (row, index) -> new Entry(row.getString("entry_type"), row.getObject("wallet_id", Long.class),
                row.getString("account_code"), row.getLong("amount"), row.getString("currency")), operation.reference());
    }

    private void assertWallet(Account account, long total, long reserved) {
        var wallet = wallets.findById(account.walletId()).orElseThrow();
        assertThat(wallet.getBalance()).isEqualTo(total);
        assertThat(wallet.getReservedBalance()).isEqualTo(reserved);
        assertThat(wallet.getAvailableBalance()).isEqualTo(total - reserved);
    }

    private Account account(String name) {
        String email = "funding-" + name + "@finwallet.test";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES (?, 'unused', ?) RETURNING id
                """, Long.class, email, name);
        Long walletId = jdbc.queryForObject("INSERT INTO wallets (user_id) VALUES (?) RETURNING id", Long.class, userId);
        return new Account(userId, walletId, email);
    }

    private String authorization(Account account) {
        return "Bearer " + jwt.generateToken(account.email(), account.userId());
    }

    private String payload(long amount, String key) {
        return """
                {"amount":%d,"idempotencyKey":"%s"}
                """.formatted(amount, key);
    }

    private record Account(Long userId, Long walletId, String email) {
    }

    private record Entry(String type, Long walletId, String accountCode, long amount, String currency) {
    }
}
