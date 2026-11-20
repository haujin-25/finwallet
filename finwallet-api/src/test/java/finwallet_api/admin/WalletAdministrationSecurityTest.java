package finwallet_api.admin;

import finwallet_api.admin.controller.WalletAdministrationController;
import finwallet_api.admin.dto.AdminAuditLog.Action;
import finwallet_api.admin.dto.AdminAuditLog.Outcome;
import finwallet_api.admin.dto.WalletAdministrationRequest;
import finwallet_api.admin.repository.AdminAuditRepository;
import finwallet_api.admin.service.WalletAdministrationService;
import finwallet_api.common.exception.GlobalExceptionHandler;
import finwallet_api.common.security.JwtAuthenticationFilter;
import finwallet_api.common.security.JwtService;
import finwallet_api.common.security.SecurityConfig;
import finwallet_api.common.security.UserPrincipal;
import finwallet_api.reconciliation.service.LedgerReconciliationService;
import finwallet_api.transfer.risk.TransferRiskAdminController;
import finwallet_api.transfer.risk.TransferRiskRepository;
import finwallet_api.transfer.risk.TransferRiskRule;
import finwallet_api.user.entity.User;
import finwallet_api.user.entity.UserRole;
import finwallet_api.user.repository.UserRepository;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(WalletAdministrationSecurityTest.Config.class)
class WalletAdministrationSecurityTest {

    private static final String SECRET = "unit-test-signing-key-for-wallet-administration-0123456789abcdefg";
    private static final String EMAIL = "operator@finwallet.test";
    private static final String WALLET_PATH = "/api/admin/wallets/11";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private WalletRepository wallets;
    @Autowired private AdminAuditRepository audit;
    @Autowired private WalletAdministrationService administration;
    @Autowired private TransferRiskRepository riskAlerts;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var wallet = new Wallet();
        wallet.setId(11L);
        when(wallets.findById(11L)).thenReturn(Optional.of(wallet));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizationUsesDatabaseRoleOnEveryRequestEvenWithAClaimedRole() throws Exception {
        String token = Jwts.builder().subject(EMAIL).claim("userId", 7L).claim("role", "ROLE_ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        setDatabaseRole(UserRole.ROLE_USER);
        mvc.perform(get(WALLET_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(WALLET_PATH).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        setDatabaseRole(UserRole.ROLE_ADMIN);
        mvc.perform(get(WALLET_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.walletId").value(11));
        setDatabaseRole(UserRole.ROLE_USER);
        mvc.perform(get(WALLET_PATH).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenWhenSignedIdentityDoesNotMatchCurrentAccount() throws Exception {
        setDatabaseRole(UserRole.ROLE_ADMIN);
        mvc.perform(get(WALLET_PATH).header("Authorization", "Bearer " + jwt.generateToken(EMAIL, 8L)))
                .andExpect(status().isUnauthorized());
        String token = jwt.generateToken(EMAIL, 7L);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidSignature";
        mvc.perform(get(WALLET_PATH).header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void methodSecurityAlsoRejectsOrdinaryDirectServiceCalls() {
        var principal = new UserPrincipal(7L, EMAIL);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThatThrownBy(() -> administration.freeze(11L, new WalletAdministrationRequest("review")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validatesAdminInputsOnAuthenticatedRequests() throws Exception {
        setDatabaseRole(UserRole.ROLE_ADMIN);
        String bearer = "Bearer " + jwt.generateToken(EMAIL, 7L);
        mvc.perform(get("/api/admin/wallets/0").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", bearer).param("size", "101"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"reason\":1}", "{\"reason\":\"a\\nb\"}",
                "{\"reason\":\"review\",\"administratorUserId\":1}")) {
            mvc.perform(post(WALLET_PATH + "/freeze").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void successfulCallsAuditTrustedPrincipalAndRepeatsDoNotChangeBalances() throws Exception {
        setDatabaseRole(UserRole.ROLE_ADMIN);
        Wallet wallet = new Wallet();
        wallet.setId(11L);
        wallet.setBalance(500L);
        wallet.reserve(200L);
        when(wallets.findByIdWithLock(11L)).thenReturn(Optional.of(wallet));
        String bearer = "Bearer " + jwt.generateToken(EMAIL, 7L);

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(WALLET_PATH + "/freeze").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"review\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FROZEN"))
                    .andExpect(jsonPath("$.balance").value(500L))
                    .andExpect(jsonPath("$.reservedBalance").value(200L));
        }

        verify(audit).append(eq(7L), eq(Action.FREEZE), eq(11L), any(), eq("review"),
                eq("ACTIVE"), eq("FROZEN"), eq(Outcome.APPLIED));
        verify(audit).append(eq(7L), eq(Action.FREEZE), eq(11L), any(), eq("review"),
                eq("FROZEN"), eq("FROZEN"), eq(Outcome.NO_CHANGE));
    }

    @Test
    void riskAlertsRequireAdminAndPaginateWithoutCustomerDetails() throws Exception {
        String path = "/api/admin/transfer-risk-alerts";
        setDatabaseRole(UserRole.ROLE_USER);
        String bearer = "Bearer " + jwt.generateToken(EMAIL, 7L);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isForbidden());

        setDatabaseRole(UserRole.ROLE_ADMIN);
        var alert = new TransferRiskRepository.Alert(1L, "TRF-example", 11L,
                TransferRiskRule.LARGE_TRANSFER, OffsetDateTime.parse("2026-11-20T16:00:00Z"));
        when(riskAlerts.readPage(2, 3)).thenReturn(List.of(alert));
        mvc.perform(get(path).header("Authorization", bearer).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rule").value("LARGE_TRANSFER"))
                .andExpect(jsonPath("$.items[0].transferReference").value("TRF-example"))
                .andExpect(jsonPath("$.items[0].email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(path).header("Authorization", bearer).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    private void setDatabaseRole(UserRole role) {
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getRole()).thenReturn(role);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, WalletAdministrationController.class,
            WalletAdministrationService.class, TransferRiskAdminController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean JwtService jwtService() { return new JwtService(SECRET, 900); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean WalletRepository wallets() { return mock(WalletRepository.class); }
        @Bean AdminAuditRepository audit() { return mock(AdminAuditRepository.class); }
        @Bean TransferRiskRepository riskAlerts() { return mock(TransferRiskRepository.class); }
        @Bean LedgerReconciliationService reconciliation() { return mock(LedgerReconciliationService.class); }
    }
}
