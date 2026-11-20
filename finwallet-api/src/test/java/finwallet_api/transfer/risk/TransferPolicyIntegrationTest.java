package finwallet_api.transfer.risk;

import finwallet_api.common.exception.InsufficientFundsException;
import finwallet_api.common.security.JwtService;
import finwallet_api.funding.dto.FundingRequest;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.service.FundingService;
import finwallet_api.funding.service.FundingSettlementService;
import finwallet_api.reconciliation.service.LedgerReconciliationService;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.dto.TransferResponse;
import finwallet_api.transfer.service.TransferService;
import finwallet_api.user.dto.UserRegistrationRequest;
import finwallet_api.user.service.UserService;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static finwallet_api.transfer.risk.TransferLimitException.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "finwallet.transfer.max-amount-sen=6000", "finwallet.transfer.daily-max-amount-sen=10000",
        "finwallet.transfer.daily-max-count=3", "finwallet.transfer.risk-large-amount-sen=5000",
        "finwallet.transfer.risk-rapid-count=2", "finwallet.transfer.risk-distinct-recipients=2"
})
@AutoConfigureMockMvc
class TransferPolicyIntegrationTest {

    private static final String BEFORE_MIDNIGHT = "2026-11-20T15:59:59.999999Z";
    private static final String MIDNIGHT = "2026-11-20T16:00:00Z";

    @Autowired private UserService users;
    @Autowired private WalletRepository wallets;
    @Autowired private FundingService funding;
    @Autowired private FundingSettlementService settlement;
    @Autowired private LedgerReconciliationService reconciliation;
    @Autowired private TransferService transfers;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;
    @MockitoBean private TransferPostingTime postingTime;
    @MockitoSpyBean private TransferRiskRepository riskRepository;

    private final List<Account> accounts = new ArrayList<>();
    private Account alice;
    private Account bob;
    private Account carol;

    @BeforeEach
    void setUp() {
        when(postingTime.now()).thenReturn(OffsetDateTime.parse(BEFORE_MIDNIGHT));
        alice = account();
        bob = account();
        carol = account();
        var deposit = funding.request(alice.userId(), FundingType.DEPOSIT, new FundingRequest(20000L, "seed", null));
        settlement.checkStatus(deposit.reference(), Scenario.SUCCESS);
    }

    @AfterEach
    void cleanUp() {
        for (Account account : accounts) {
            long id = account.walletId();
            jdbc.update("""
                    DELETE FROM transfer_risk_alerts WHERE transfer_id IN
                        (SELECT id FROM transfers WHERE sender_wallet_id = ? OR recipient_wallet_id = ?)
                    """, id, id);
            jdbc.update("""
                    DELETE FROM ledger_entries WHERE transfer_id IN
                        (SELECT id FROM transfers WHERE sender_wallet_id = ? OR recipient_wallet_id = ?)
                    """, id, id);
            jdbc.update("""
                    DELETE FROM transfers WHERE (sender_wallet_id = ? OR recipient_wallet_id = ?)
                        AND reversal_of_transfer_id IS NOT NULL
                    """, id, id);
            jdbc.update("DELETE FROM transfers WHERE sender_wallet_id = ? OR recipient_wallet_id = ?", id, id);
            jdbc.update("""
                    DELETE FROM funding_ledger_entries WHERE operation_id IN
                        (SELECT id FROM funding_operations WHERE wallet_id = ?)
                    """, id);
            jdbc.update("DELETE FROM funding_operations WHERE wallet_id = ?", id);
            jdbc.update("DELETE FROM wallets WHERE id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", account.userId());
        }
    }

    @Test
    void enforcesExactPerTransferAndDailyAmountAndUsesTheSameInstantAtMidnight() throws Exception {
        mvc.perform(post("/api/transfers").header("Authorization", authorization(alice))
                        .contentType("application/json")
                        .content("{\"recipientEmail\":\"%s\",\"amount\":6001,\"idempotencyKey\":\"per-transfer\"}"
                                .formatted(bob.email())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFER_AMOUNT_LIMIT"));
        var first = send(alice, bob, 6000, "first");
        assertThat(first.createdAt().toInstant()).isEqualTo(Instant.parse(BEFORE_MIDNIGHT));
        assertThat(jdbc.queryForObject("SELECT created_at FROM transfers WHERE reference = ?",
                OffsetDateTime.class, first.transferReference()).toInstant()).isEqualTo(first.createdAt().toInstant());
        send(alice, carol, 4000, "second");
        assertLimit(alice, bob, 1, "over-day", DAILY_TRANSFER_AMOUNT_LIMIT);

        when(postingTime.now()).thenReturn(OffsetDateTime.parse(MIDNIGHT));
        assertThat(send(alice, bob, 6000, "first")).isEqualTo(first);
        var nextDay = send(alice, bob, 1, "over-day");
        assertThat(nextDay.createdAt().toInstant()).isEqualTo(Instant.parse(MIDNIGHT));
        assertThat(alerts(first.transferReference())).containsExactly("LARGE_TRANSFER");
    }

    @Test
    void enforcesDailyCountWithoutCountingIncomingFailedOrPendingFundingOperations() {
        funding.request(alice.userId(), FundingType.DEPOSIT, new FundingRequest(500L, "pending", null));
        jdbc.update("""
                INSERT INTO transfers (reference, sender_wallet_id, recipient_wallet_id, amount, currency,
                                       status, idempotency_key, created_at)
                VALUES (?, ?, ?, 6000, 'MYR', 'FAILED', 'failed', ?)
                """, "FAILED-" + UUID.randomUUID(), alice.walletId(), bob.walletId(),
                OffsetDateTime.parse(BEFORE_MIDNIGHT));
        send(alice, bob, 100, "one");
        send(bob, alice, 100, "incoming");
        send(alice, bob, 100, "two");
        send(alice, bob, 100, "three");
        assertLimit(alice, bob, 1, "four", DAILY_TRANSFER_COUNT_LIMIT);
    }

    @Test
    void reversalsDoNotRefundQuotaOrConsumeTheReversingWalletsQuota() {
        var first = send(alice, bob, 6000, "first");
        var second = send(alice, bob, 4000, "second");
        var reversal = transfers.reverse(first.transferReference());
        transfers.reverse(second.transferReference());
        assertThat(transfers.reverse(first.transferReference())).isEqualTo(reversal);
        assertLimit(alice, bob, 1, "over-day", DAILY_TRANSFER_AMOUNT_LIMIT);
        assertThat(alerts(reversal.transferReference())).isEmpty();
        var deposit = funding.request(bob.userId(), FundingType.DEPOSIT, new FundingRequest(10000L, "seed", null));
        settlement.checkStatus(deposit.reference(), Scenario.SUCCESS);
        send(bob, alice, 6000, "outgoing");
    }

    @Test
    void concurrentRequestsWithDifferentRecipientsCannotExceedDailyAmount() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race(bob, "first", 6000, ready, start));
            var second = executor.submit(() -> race(carol, "second", 6000, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED", "DAILY_TRANSFER_AMOUNT_LIMIT");
        } finally {
            start.countDown();
        }
        assertThat(wallets.findById(alice.walletId()).orElseThrow().getAvailableBalance()).isEqualTo(14000);
    }

    @Test
    void concurrentTransfersCannotSpendThePendingWithdrawalReserve() throws Exception {
        funding.request(alice.userId(), FundingType.WITHDRAWAL, new FundingRequest(12000L, "hold", null));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race(bob, "first", 4500, ready, start));
            var second = executor.submit(() -> race(carol, "second", 4500, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED", "INSUFFICIENT_FUNDS");
        } finally {
            start.countDown();
        }
        var wallet = wallets.findById(alice.walletId()).orElseThrow();
        assertThat(wallet.getReservedBalance()).isEqualTo(12000);
        assertThat(wallet.getAvailableBalance()).isEqualTo(3500);
    }

    @Test
    void keepsReservationsAndWritesDistinctRiskRulesOncePerPosting() {
        funding.request(alice.userId(), FundingType.WITHDRAWAL, new FundingRequest(12000L, "hold", null));
        var first = send(alice, bob, 5000, "first");
        var repeatedRecipient = send(alice, bob, 1000, "repeat-recipient");
        var second = send(alice, carol, 1000, "second-recipient");
        assertThatThrownBy(() -> send(alice, carol, 1001, "locked"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(send(alice, carol, 1000, "second-recipient")).isEqualTo(second);
        assertThat(alerts(first.transferReference())).containsExactly("LARGE_TRANSFER");
        assertThat(alerts(repeatedRecipient.transferReference())).containsExactly("RAPID_TRANSFERS");
        assertThat(alerts(second.transferReference())).containsExactly("DISTINCT_RECIPIENTS", "RAPID_TRANSFERS");
        assertThat(wallets.findById(alice.walletId()).orElseThrow().getAvailableBalance()).isEqualTo(1000);
    }

    @Test
    void alertPersistenceFailureRollsBackTheTransferAndItsFinancialEffects() {
        var baseline = reconciliation.reconcile().discrepancies();
        TransferRiskRepository target = AopTestUtils.getUltimateTargetObject(riskRepository);
        doAnswer(call -> {
            call.callRealMethod();
            throw new DataIntegrityViolationException("Injected risk persistence failure");
        }).when(target).append(any(), any());

        assertThatThrownBy(() -> send(alice, bob, 6000, "rollback"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers WHERE sender_wallet_id = ?",
                Long.class, alice.walletId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE wallet_id IN (?, ?)",
                Long.class, alice.walletId(), bob.walletId())).isZero();
        assertThat(wallets.findById(alice.walletId()).orElseThrow().getBalance()).isEqualTo(20000);
        assertThat(wallets.findById(bob.walletId()).orElseThrow().getBalance()).isZero();
        assertThat(reconciliation.reconcile().discrepancies()).containsExactlyElementsOf(baseline);
    }

    @Test
    void riskReportRequiresAdminAndReturnsOnlyOperationalIdentifiers() throws Exception {
        var posted = send(alice, bob, 6000, "first");
        String path = "/api/admin/transfer-risk-alerts";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", authorization(alice))).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role = 'ROLE_ADMIN' WHERE id = ?", carol.userId());
        mvc.perform(get(path).header("Authorization", authorization(carol)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.transferReference == '%s')].rule"
                        .formatted(posted.transferReference())).value(hasItem("LARGE_TRANSFER")))
                .andExpect(jsonPath("$.items[0].email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].description").doesNotHaveJsonPath());
    }

    private String race(Account recipient, String key, long amount, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent test to start");
        }
        try {
            return send(alice, recipient, amount, key).status();
        } catch (TransferLimitException exception) {
            return exception.code().name();
        } catch (InsufficientFundsException exception) {
            return "INSUFFICIENT_FUNDS";
        }
    }

    private Account account() {
        String email = "policy-" + UUID.randomUUID() + "@finwallet.test";
        var user = users.register(new UserRegistrationRequest(email, "Integration-test-only", "Policy Test"));
        var account = new Account(user.id(), wallets.findIdByUserId(user.id()).orElseThrow(), email);
        accounts.add(account);
        return account;
    }

    private void assertLimit(Account sender, Account recipient, long amount, String key,
                             TransferLimitException.Code code) {
        assertThatThrownBy(() -> send(sender, recipient, amount, key))
                .isInstanceOfSatisfying(TransferLimitException.class, error -> assertThat(error.code()).isEqualTo(code));
    }

    private TransferResponse send(Account sender, Account recipient, long amount, String key) {
        return transfers.transfer(sender.userId(), new TransferRequest(recipient.email(), amount, null, key));
    }

    private List<String> alerts(String reference) {
        return jdbc.queryForList("""
                SELECT a.rule FROM transfer_risk_alerts a JOIN transfers t ON t.id = a.transfer_id
                WHERE t.reference = ? ORDER BY a.rule
                """, String.class, reference);
    }

    private String authorization(Account account) {
        return "Bearer " + jwt.generateToken(account.email(), account.userId());
    }

    private record Account(long userId, long walletId, String email) {}
}
