package finwallet_api.funding;

import finwallet_api.common.exception.GlobalExceptionHandler;
import finwallet_api.common.security.UserPrincipal;
import finwallet_api.funding.controller.FundingController;
import finwallet_api.funding.dto.FundingRequest;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.service.FundingService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FundingControllerTest {

    private final FundingService funding = mock(FundingService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var principal = new UserPrincipal(12L, "customer@finwallet.test");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new FundingController(funding))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputBeforeCallingFundingService(String body) throws Exception {
        mvc.perform(post("/api/deposits").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(funding);
    }

    static Stream<String> invalidRequests() {
        return Stream.of("", "{", "[]", "{}", "{\"amount\":1}", "{\"idempotencyKey\":\"key\"}",
                payload("null", "key"), payload("0", "key"), payload("-1", "key"),
                payload("1.0", "key"), payload("\"1\"", "key"), payload("true", "key"),
                payload("9223372036854775808", "key"), payload("1", " "),
                payload("1", "invalid/key"), payload("1", "x".repeat(256)),
                payload("1", "key") + " {}",
                payload("1", "key").replace("}", ",\"description\":1}"),
                payload("1", "key").replace("}", ",\"description\":\"" + "x".repeat(501) + "\"}"),
                payload("1", "key").replace("}", ",\"currency\":\"USD\"}"),
                payload("1", "key").replace("}", ",\"userId\":1}"),
                payload("1", "key").replace("}", ",\"outcome\":\"SUCCESS\"}"));
    }

    @Test
    void withdrawalTakesOwnerFromPrincipalAndReturnsPendingResponse() throws Exception {
        var request = new FundingRequest(500L, "payment:1", null);
        when(funding.request(12L, FundingType.WITHDRAWAL, request))
                .thenReturn(new FundingResponse("WDR-example", FundingType.WITHDRAWAL, FundingStatus.PENDING,
                        500, "MYR", null, OffsetDateTime.now(), null));

        mvc.perform(post("/api/withdrawals").param("walletId", "64").param("userId", "65")
                        .contentType(MediaType.APPLICATION_JSON).content(payload("500", "payment:1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("WDR-example"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerReference").doesNotHaveJsonPath());
        verify(funding).request(12L, FundingType.WITHDRAWAL, request);
    }

    private static String payload(String amount, String key) {
        return """
                {"amount":%s,"idempotencyKey":"%s"}
                """.formatted(amount, key);
    }
}
