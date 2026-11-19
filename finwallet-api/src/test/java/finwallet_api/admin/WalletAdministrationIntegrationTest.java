package finwallet_api.admin;

import finwallet_api.admin.dto.WalletAdministrationRequest;
import finwallet_api.admin.repository.AdminAuditRepository;
import finwallet_api.admin.service.WalletAdministrationService;
import finwallet_api.common.exception.InactiveWalletException;
import finwallet_api.common.security.JwtService;
import finwallet_api.common.security.UserPrincipal;
import finwallet_api.funding.dto.FundingRequest;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.service.FundingService;
import finwallet_api.funding.service.FundingSettlementService;
import finwallet_api.reconciliation.repository.ReconciliationRepository;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.service.TransferService;
import finwallet_api.wallet.repository.WalletRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletAdministrationIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private TransactionTemplate transactions;
    @Autowired private WalletRepository wallets;
    @Autowired private WalletAdministrationService administration;
    @Autowired private TransferService transfers;
    @Autowired private FundingService funding;
    @Autowired private FundingSettlementService settlement;
    @Value("${jwt.secret}") private String signingSecret;

    @MockitoSpyBean private AdminAuditRepository audit;
    @MockitoSpyBean private ReconciliationRepository reconciliation;

    private Account admin;
    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> {
            admin = account("operator");
            alice = account("alice");
            bob = account("bob");
            jdbc.update("UPDATE users SET role = 'ROLE_ADMIN' WHERE id = ?", admin.userId());
        });
    }

    @AfterEach
    void cleanUp() {
        if (bob == null) {
            return;
        }
        transactions.executeWithoutResult(transaction -> {
            jdbc.update("DELETE FROM admin_audit_logs WHERE administrator_user_id = ?", admin.userId());
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
            jdbc.update("DELETE FROM wallets WHERE id IN (?, ?, ?)", alice.walletId(), bob.walletId(), admin.walletId());
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", alice.userId(), bob.userId(), admin.userId());
        });
    }

    @Test
    void registrationAlwaysPersistsUserRoleAndIgnoresPrivilegeAssignments() throws Exception {
        String ordinary = "admin-test-new@finwallet.test";
        String attempted = "admin-test-attempt@finwallet.test";
        try {
            register(ordinary, "");
            register(attempted, ",\"role\":\"ROLE_ADMIN\",\"roles\":[\"ROLE_ADMIN\"],\"authorities\":[\"ROLE_ADMIN\"]");
            assertThat(jdbc.queryForList("SELECT role FROM users WHERE email IN (?, ?)", String.class,
                    ordinary, attempted)).containsExactly("ROLE_USER", "ROLE_USER");
        } finally {
            transactions.executeWithoutResult(transaction -> {
                jdbc.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email IN (?, ?))",
                        ordinary, attempted);
                jdbc.update("DELETE FROM users WHERE email IN (?, ?)", ordinary, attempted);
            });
        }
    }

    @Test
    void namespaceRejectsAnonymousAndOrdinaryUsers() throws Exception {
        for (String path : List.of("/api/admin/wallets/" + alice.walletId(),
                "/api/admin/audit-logs", "/api/admin/reconciliation")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", bearer(alice))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/wallets/{walletId}/freeze", alice.walletId())
                        .header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"attempt\"}"))
                .andExpect(status().isForbidden());
        assertWallet("ACTIVE", 0, 0);
        assertThat(auditCount()).isZero();
    }

    @Test
    void methodAuthorizationProtectsDirectServiceCalls() {
        var principal = new UserPrincipal(alice.userId(), alice.email());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        try {
            assertThatThrownBy(() -> administration.freeze(alice.walletId(), new WalletAdministrationRequest("attempt")))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(administration::reconcile).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(auditCount()).isZero();
    }

    @Test
    void databaseRoleWinsOverVerifiedClaimsAndRevocationWorksWithSameToken() throws Exception {
        String token = Jwts.builder().subject(alice.email()).claim("userId", alice.userId())
                .claim("role", "ROLE_ADMIN").claim("authorities", List.of("ROLE_ADMIN"))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/admin/wallets/{walletId}", alice.walletId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String adminToken = bearer(admin);
        mvc.perform(get("/api/admin/wallets/{walletId}", alice.walletId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").doesNotHaveJsonPath());
        jdbc.update("UPDATE users SET role = 'ROLE_USER' WHERE id = ?", admin.userId());
        mvc.perform(get("/api/admin/wallets/{walletId}", alice.walletId()).header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/wallets/me").header("Authorization", adminToken)).andExpect(status().isOk());
    }

    @Test
    void freezeAndUnfreezeAreIdempotentPreserveHoldsAndAuditEverySuccessfulCall() throws Exception {
        fund(alice, 1000);
        requestWithdrawal(alice, 600);
        change("freeze", "Review activity").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));
        change("freeze", "Repeat freeze").andExpect(status().isOk());
        assertWallet("FROZEN", 1000, 600);
        change("unfreeze", "Review complete").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        change("unfreeze", "Repeat unfreeze").andExpect(status().isOk());
        assertWallet("ACTIVE", 1000, 600);
        assertThat(jdbc.queryForList("""
                SELECT action || ':' || previous_status || ':' || new_status || ':' || outcome
                FROM admin_audit_logs WHERE administrator_user_id = ? AND wallet_id = ? ORDER BY id
                """, String.class, admin.userId(), alice.walletId())).containsExactly(
                "FREEZE:ACTIVE:FROZEN:APPLIED", "FREEZE:FROZEN:FROZEN:NO_CHANGE",
                "UNFREEZE:FROZEN:ACTIVE:APPLIED", "UNFREEZE:ACTIVE:ACTIVE:NO_CHANGE");
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(admin)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].administratorUserId").value(admin.userId()))
                .andExpect(jsonPath("$.items[0].walletId").value(alice.walletId()))
                .andExpect(jsonPath("$.items[0].reason").value("Repeat unfreeze"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE wallet_id = ?",
                Long.class, alice.walletId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM funding_ledger_entries WHERE wallet_id = ?",
                Long.class, alice.walletId())).isEqualTo(1L);
    }

    @Test
    void frozenWalletBlocksNewMoneyMovementButExistingPendingFundingCanSettle() throws Exception {
        fund(alice, 1000);
        fund(bob, 1000);
        var original = transfers.transfer(alice.userId(), new TransferRequest(bob.email(), 100L, null, "reversal"));
        var pending = requestWithdrawal(alice, 300);
        var deposit = funding.request(alice.userId(), FundingType.DEPOSIT, new FundingRequest(50L, "pending", null));
        change("freeze", "Review").andExpect(status().isOk());

        assertThatThrownBy(() -> spend(false)).isInstanceOf(InactiveWalletException.class);
        assertThatThrownBy(() -> transfers.transfer(bob.userId(), new TransferRequest(alice.email(), 100L, null, "in")))
                .isInstanceOf(InactiveWalletException.class);
        assertThatThrownBy(() -> funding.request(alice.userId(), FundingType.WITHDRAWAL,
                new FundingRequest(100L, "new-withdrawal", null))).isInstanceOf(InactiveWalletException.class);
        assertThatThrownBy(() -> funding.request(alice.userId(), FundingType.DEPOSIT,
                new FundingRequest(100L, "new-deposit", null))).isInstanceOf(InactiveWalletException.class);
        assertThatThrownBy(() -> transfers.reverse(original.transferReference())).isInstanceOf(InactiveWalletException.class);
        assertWallet("FROZEN", 900, 300);

        assertThat(settlement.checkStatus(pending.reference(), Scenario.SUCCESS).status()).isEqualTo(FundingStatus.COMPLETED);
        assertThat(settlement.checkStatus(deposit.reference(), Scenario.SUCCESS).status()).isEqualTo(FundingStatus.COMPLETED);
        assertWallet("FROZEN", 650, 0);
        change("unfreeze", "Review complete").andExpect(status().isOk());
        transfers.reverse(original.transferReference());
        spend(false);
        assertWallet("ACTIVE", 550, 0);
    }

    @Test
    void invalidWalletIdsStatesAndReasonsAreRejectedWithoutAnAuditRow() throws Exception {
        mvc.perform(get("/api/admin/wallets/0").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/wallets/{walletId}", Long.MAX_VALUE).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer(admin)).param("size", "101"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{", "{}", "{\"reason\":7}", "{\"reason\":\" \"}",
                "{\"reason\":\"bad\\nreason\"}", "{\"reason\":\"" + "x".repeat(501) + "\"}",
                "{\"reason\":\"review\",\"administratorUserId\":1}")) {
            mvc.perform(post("/api/admin/wallets/{walletId}/freeze", alice.walletId())
                            .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE wallets SET status = 'SUSPENDED' WHERE id = ?", alice.walletId());
        change("freeze", "Review").andExpect(status().isConflict());
        change("unfreeze", "Review").andExpect(status().isConflict());
        assertWallet("SUSPENDED", 0, 0);
        assertThat(auditCount()).isZero();
    }

    @Test
    void auditFailureRollsBackBothFlushedStatusAndAuditInsert() throws Exception {
        AdminAuditRepository target = AopTestUtils.getUltimateTargetObject(audit);
        try {
            doAnswer(invocation -> {
                wallets.flush();
                assertWallet("FROZEN", 0, 0);
                invocation.callRealMethod();
                assertThat(auditCount()).isEqualTo(1);
                throw new DataIntegrityViolationException("Post-insert test failure");
            }).when(target).append(anyLong(), any(), anyLong(), any(), anyString(), anyString(), anyString(), any());

            change("freeze", "Will roll back").andExpect(status().isInternalServerError())
                    .andExpect(content().string("Unable to complete the request"));
            assertWallet("ACTIVE", 0, 0);
            assertThat(auditCount()).isZero();
        } finally {
            reset(target);
        }
    }

    @Test
    void administratorReconciliationUsesReadOnlySnapshotAndHasNoFinancialControls() throws Exception {
        ReconciliationRepository target = AopTestUtils.getUltimateTargetObject(reconciliation);
        doAnswer(invocation -> {
            assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            return invocation.callRealMethod();
        }).when(target).countRecords();

        mvc.perform(get("/api/admin/reconciliation").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discrepancies").isArray());
        mvc.perform(post("/api/funding/unknown/confirm").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/transfers/unknown/reverse").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        assertWallet("ACTIVE", 0, 0);
        assertThat(auditCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void freezeAndFinancialWritesHonorTheSameRowLock(boolean withdrawal, boolean freezeFirst) throws Exception {
        fund(alice, 1000);
        var holdingPid = new CompletableFuture<Integer>();
        var release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        AdminAuditRepository target = AopTestUtils.getUltimateTargetObject(audit);
        try {
            if (freezeFirst) {
                doAnswer(invocation -> {
                    holdWalletLock(holdingPid, release);
                    return invocation.callRealMethod();
                }).when(target).append(anyLong(), any(), anyLong(), any(), anyString(), anyString(), anyString(), any());
            }
            Runnable financialOperation = () -> transactions.executeWithoutResult(transaction -> {
                spend(withdrawal);
                if (!freezeFirst) {
                    holdWalletLock(holdingPid, release);
                }
            });
            var first = freezeFirst
                    ? workers.submit(() -> change("freeze", "First writer").andExpect(status().isOk()))
                    : workers.submit(financialOperation);
            int blocker = holdingPid.get(10, TimeUnit.SECONDS);
            var second = freezeFirst ? workers.submit(financialOperation)
                    : workers.submit(() -> change("freeze", "Second writer").andExpect(status().isOk()));
            awaitBlockedBy(blocker);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            if (freezeFirst) {
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(InactiveWalletException.class);
                assertWallet("FROZEN", 1000, 0);
            } else {
                second.get(10, TimeUnit.SECONDS);
                assertWallet("FROZEN", withdrawal ? 1000 : 800, withdrawal ? 300 : 0);
            }
            assertThat(auditCount()).isEqualTo(1);
        } finally {
            release.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            if (freezeFirst) {
                reset(target);
            }
        }
    }

    private void holdWalletLock(CompletableFuture<Integer> holdingPid, CountDownLatch release) {
        holdingPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
        try {
            if (!release.await(12, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release wallet lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding wallet lock", exception);
        }
    }

    private void awaitBlockedBy(int blocker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))
                    """, Boolean.class, blocker))) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Expected the second operation to wait on the wallet lock");
    }

    private void spend(boolean withdrawal) {
        if (withdrawal) {
            requestWithdrawal(alice, 300);
        } else {
            transfers.transfer(alice.userId(), new TransferRequest(bob.email(), 200L, null, "spend"));
        }
    }

    private FundingResponse requestWithdrawal(Account account, long amount) {
        return funding.request(account.userId(), FundingType.WITHDRAWAL, new FundingRequest(amount, "withdrawal", null));
    }

    private void fund(Account account, long amount) {
        var request = funding.request(account.userId(), FundingType.DEPOSIT, new FundingRequest(amount, "seed", null));
        settlement.checkStatus(request.reference(), Scenario.SUCCESS);
    }

    private ResultActions change(String action, String reason) throws Exception {
        return mvc.perform(post("/api/admin/wallets/{walletId}/{action}", alice.walletId(), action)
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private void register(String email, String extraFields) throws Exception {
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"TestPassword123!","fullName":"New customer"%s}
                """.formatted(email, extraFields))).andExpect(status().isCreated());
    }

    private String bearer(Account account) {
        return "Bearer " + jwt.generateToken(account.email(), account.userId());
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs WHERE administrator_user_id = ?",
                Long.class, admin.userId());
    }

    private void assertWallet(String status, long balance, long reserved) {
        assertThat(jdbc.queryForMap("SELECT status, balance, reserved_balance FROM wallets WHERE id = ?", alice.walletId()))
                .containsEntry("status", status).containsEntry("balance", balance).containsEntry("reserved_balance", reserved);
    }

    private Account account(String name) {
        String email = "admin-test-" + name + "@finwallet.test";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES (?, 'unused', ?) RETURNING id
                """, Long.class, email, name);
        Long walletId = jdbc.queryForObject("INSERT INTO wallets (user_id) VALUES (?) RETURNING id", Long.class, userId);
        return new Account(userId, walletId, email);
    }

    private record Account(Long userId, Long walletId, String email) {
    }
}
