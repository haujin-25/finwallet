package finwallet_api.transfer;

import finwallet_api.common.security.JwtService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferHistoryIntegrationTest {

    private static final OffsetDateTime TIED_TIME = OffsetDateTime.parse("2026-11-18T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;

    private Account alice;
    private Account bob;
    private Account carol;
    private Account david;

    @BeforeEach
    void setUp() {
        alice = account("alice", "Alice Example");
        bob = account("bob", "Bob Example");
        carol = account("carol", "Carol Example");
        david = account("david", "David Example");

        transfer("history-old", carol, alice, TIED_TIME.minusHours(1));
        transfer("history-out", alice, bob, TIED_TIME);
        transfer("history-in", bob, alice, TIED_TIME);
        transfer("history-unrelated", bob, carol, TIED_TIME.plusHours(1));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(get("/api/transfers/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyOwnersTransfersAndSafeCounterpartyFields() throws Exception {
        mvc.perform(get("/api/transfers/me")
                        .header("Authorization", alice.authorization())
                        .param("walletId", bob.walletId().toString())
                        .param("userId", bob.userId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].transferReference").value("history-in"))
                .andExpect(jsonPath("$.items[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.items[0].counterpartyName").value("Bob Example"))
                .andExpect(jsonPath("$.items[0].amount").value(250))
                .andExpect(jsonPath("$.items[0].currency").value("MYR"))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].description").value("Example"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-18T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].operationType").value("TRANSFER"))
                .andExpect(jsonPath("$.items[0].reversalOfReference").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].length()").value(9))
                .andExpect(jsonPath("$.items[1].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.items[2].transferReference").value("history-old"));
    }

    @Test
    void emptyHistoryHasNoResultsEvenWithSomeoneElsesWalletId() throws Exception {
        mvc.perform(get("/api/transfers/me").header("Authorization", david.authorization())
                        .param("walletId", alice.walletId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void paginatesTiesByDescendingId() throws Exception {
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transferReference").value("history-in"))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization())
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items[0].transferReference").value("history-out"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization())
                        .param("page", "9").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void filtersDirectionAndUsesInclusiveFromAndExclusiveTo() throws Exception {
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization())
                        .param("direction", "OUTGOING")
                        .param("from", "2026-11-18T18:00:00+08:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].transferReference").value("history-out"));
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization())
                        .param("direction", "INCOMING").param("to", "2026-11-18T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].transferReference").value("history-old"));
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1", "page,1.5", "page,2147483647", "size,0", "size,101",
            "direction,SIDEWAYS", "direction,''", "from,2026-11-18T10:00:00",
            "to,2026-11-18T10:00:00.000000001Z"
    })
    void invalidParametersReturn400(String name, String value) throws Exception {
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization()).param(name, value))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRangeReturns400AndInternalReconciliationHasNoHttpRoute() throws Exception {
        mvc.perform(get("/api/transfers/me").header("Authorization", alice.authorization())
                        .param("from", "2026-11-18T11:00:00Z").param("to", "2026-11-18T10:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/internal/reconciliation").header("Authorization", alice.authorization()))
                .andExpect(status().isNotFound());
    }

    private Account account(String name, String fullName) {
        String email = "history-" + name + "@finwallet.test";
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES (?, 'unused', ?) RETURNING id
                """, Long.class, email, fullName);
        Long walletId = jdbc.queryForObject("INSERT INTO wallets (user_id) VALUES (?) RETURNING id",
                Long.class, userId);
        return new Account(userId, walletId, "Bearer " + jwtService.generateToken(email, userId));
    }

    private void transfer(String reference, Account sender, Account recipient, OffsetDateTime time) {
        jdbc.update("""
                INSERT INTO transfers
                    (reference, sender_wallet_id, recipient_wallet_id, amount, currency,
                     description, idempotency_key, created_at)
                VALUES (?, ?, ?, 250, 'MYR', 'Example', ?, ?)
                """, reference, sender.walletId(), recipient.walletId(), reference, time);
    }

    private record Account(Long userId, Long walletId, String authorization) {
    }
}
