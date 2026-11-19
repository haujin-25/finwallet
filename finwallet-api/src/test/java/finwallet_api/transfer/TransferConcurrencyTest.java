package finwallet_api.transfer;

import finwallet_api.common.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import finwallet_api.transfer.entity.LedgerEntry;
import finwallet_api.transfer.entity.Transfer;
import finwallet_api.transfer.repository.LedgerEntryRepository;
import finwallet_api.transfer.repository.TransferRepository;
import finwallet_api.user.entity.User;
import finwallet_api.user.repository.UserRepository;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrency tests for the transfer endpoint.
 *
 * <p>These tests do NOT use @Transactional. Concurrent threads need to operate on committed
 * database rows to exercise real PostgreSQL row-level locking. Each test commits its data
 * and cleans up manually in @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User userA;
    private User userB;
    private User userC;
    private Wallet walletA;
    private Wallet walletB;
    private Wallet walletC;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        userA = createUser("concurrency-a@finwallet.test", now);
        walletA = createWallet(userA.getId(), 50000L, now);
        tokenA = jwtService.generateToken(userA.getEmail(), userA.getId());

        userB = createUser("concurrency-b@finwallet.test", now);
        walletB = createWallet(userB.getId(), 50000L, now);
        tokenB = jwtService.generateToken(userB.getEmail(), userB.getId());

        userC = createUser("concurrency-c@finwallet.test", now);
        walletC = createWallet(userC.getId(), 0L, now);
    }

    @AfterEach
    void tearDown() {
        // Delete in FK dependency order: ledger_entries → transfers → wallets → users
        ledgerEntryRepository.deleteAll(
                ledgerEntryRepository.findAll().stream()
                        .filter(e -> isOwnedByTestWallet(e.getWalletId()))
                        .toList()
        );
        transferRepository.deleteAll(
                transferRepository.findAll().stream()
                        .filter(t -> isOwnedByTestWallet(t.getSenderWalletId())
                                || isOwnedByTestWallet(t.getRecipientWalletId()))
                        .toList()
        );
        safeDeleteWallet(walletA);
        safeDeleteWallet(walletB);
        safeDeleteWallet(walletC);
        safeDeleteUser(userA);
        safeDeleteUser(userB);
        safeDeleteUser(userC);
    }

    private boolean isOwnedByTestWallet(Long walletId) {
        return walletId != null && (walletId.equals(walletA.getId())
                || walletId.equals(walletB.getId())
                || walletId.equals(walletC.getId()));
    }

    private void safeDeleteWallet(Wallet wallet) {
        if (wallet != null && wallet.getId() != null) {
            walletRepository.findById(wallet.getId()).ifPresent(walletRepository::delete);
        }
    }

    private void safeDeleteUser(User user) {
        if (user != null && user.getId() != null) {
            userRepository.findById(user.getId()).ifPresent(userRepository::delete);
        }
    }

    @Test
    void concurrentTransfersFromSameSenderCannotOverspend() throws Exception {
        List<Callable<MvcResult>> requests = IntStream.range(0, 10)
                .mapToObj(index -> request(tokenA, userC.getEmail(), 8000L, "Transfer", "spend-" + index))
                .toList();

        List<MvcResult> results = runConcurrently(requests);

        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 201).hasSize(6);
        assertThat(results).filteredOn(result -> result.getResponse().getStatus() == 422).hasSize(4);
        assertBalancesAndLedger(6, 2000L, 50000L, 48000L);
    }

    @Test
    void concurrentIdenticalRequestsReplayOneTransferEvenAfterFundsAreSpent() throws Exception {
        Callable<MvcResult> transfer = request(tokenA, userC.getEmail(), 50000L, "Same request", "shared-key");
        List<MvcResult> results = runConcurrently(List.of(transfer, transfer, transfer, transfer, transfer));

        assertThat(results).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        String original = results.getFirst().getResponse().getContentAsString();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getContentAsString()).isEqualTo(original);
        }
        String reference = JsonPath.read(original, "$.transferReference");
        assertThat(ownedTransfers()).extracting(Transfer::getReference).containsExactly(reference);
        assertBalancesAndLedger(1, 0L, 50000L, 50000L);
    }

    @Test
    void concurrentSameKeyWithDifferentDescriptionsReturnsConflict() throws Exception {
        List<MvcResult> results = runConcurrently(List.of(
                request(tokenA, userC.getEmail(), 50000L, "First description", "conflict"),
                request(tokenA, userC.getEmail(), 50000L, "Second description", "conflict")));

        assertThat(results).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(201, 409);
        assertBalancesAndLedger(1, 0L, 50000L, 50000L);
    }

    @Test
    void oppositeDirectionTransfersCompleteWithoutDeadlock() throws Exception {
        List<Callable<MvcResult>> requests = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            // Both senders may independently use the same key.
            String key = "opposite-" + index;
            requests.add(request(tokenA, userB.getEmail(), 3000L, "A to B", key));
            requests.add(request(tokenB, userA.getEmail(), 2000L, "B to A", key));
        }

        assertThat(runConcurrently(requests))
                .allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));
        assertBalancesAndLedger(10, 45000L, 55000L, 0L);
    }

    private Callable<MvcResult> request(String token, String recipient, long amount, String description, String key) {
        return () -> mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientEmail": "%s",
                                  "amount": %d,
                                  "description": "%s",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(recipient, amount, description, key)))
                .andReturn();
    }

    private List<MvcResult> runConcurrently(List<Callable<MvcResult>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return request.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Transfer> ownedTransfers() {
        return transferRepository.findAll().stream()
                .filter(transfer -> isOwnedByTestWallet(transfer.getSenderWalletId()))
                .toList();
    }

    private void assertBalancesAndLedger(int transferCount, long balanceA, long balanceB, long balanceC) {
        List<Transfer> transfers = ownedTransfers();
        assertThat(transfers).hasSize(transferCount);
        List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                .filter(entry -> isOwnedByTestWallet(entry.getWalletId()))
                .toList();
        assertThat(entries).hasSize(2 * transferCount);

        for (Transfer transfer : transfers) {
            assertThat(entries).filteredOn(entry -> entry.getTransferId().equals(transfer.getId()))
                    .hasSize(2)
                    .allSatisfy(entry -> {
                        assertThat(entry.getAmount()).isEqualTo(transfer.getAmount());
                        assertThat(entry.getCurrency()).isEqualTo(transfer.getCurrency());
                    })
                    .extracting(LedgerEntry::getEntryType, LedgerEntry::getWalletId)
                    .containsExactlyInAnyOrder(
                            tuple("DEBIT", transfer.getSenderWalletId()),
                            tuple("CREDIT", transfer.getRecipientWalletId()));
        }
        assertLedgerBalance(entries, walletA, balanceA);
        assertLedgerBalance(entries, walletB, balanceB);
        assertLedgerBalance(entries, walletC, balanceC);
    }

    private void assertLedgerBalance(List<LedgerEntry> entries, Wallet initialWallet, long expectedBalance) {
        long netChange = entries.stream()
                .filter(entry -> entry.getWalletId().equals(initialWallet.getId()))
                .mapToLong(entry -> "CREDIT".equals(entry.getEntryType()) ? entry.getAmount() : -entry.getAmount())
                .sum();
        Wallet currentWallet = walletRepository.findById(initialWallet.getId()).orElseThrow();
        assertThat(currentWallet.getBalance()).isEqualTo(expectedBalance)
                .isEqualTo(initialWallet.getBalance() + netChange);
    }

    private User createUser(String email, OffsetDateTime now) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Password123!"));
        u.setFullName("Concurrency User");
        u.setStatus("ACTIVE");
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return userRepository.saveAndFlush(u);
    }

    private Wallet createWallet(Long userId, long balance, OffsetDateTime now) {
        Wallet w = new Wallet();
        w.setUserId(userId);
        w.setCurrency("MYR");
        w.setBalance(balance);
        w.setStatus("ACTIVE");
        w.setCreatedAt(now);
        w.setUpdatedAt(now);
        return walletRepository.saveAndFlush(w);
    }
}
