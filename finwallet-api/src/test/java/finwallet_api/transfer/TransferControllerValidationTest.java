package finwallet_api.transfer;

import finwallet_api.common.exception.GlobalExceptionHandler;
import finwallet_api.common.security.UserPrincipal;
import finwallet_api.transfer.controller.TransferController;
import finwallet_api.transfer.dto.TransferRequest;
import finwallet_api.transfer.dto.TransferResponse;
import finwallet_api.transfer.service.TransferService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerValidationTest {

    private static final String INVALID_JSON = "Invalid JSON request body";
    private static final String EMAIL = "recipient@finwallet.test";

    private final TransferService service = mock(TransferService.class);
    private final UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(1L, "sender@finwallet.test"), null, List.of());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TransferController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidRequestsBeforeCallingService(String body, String expectedMessage) throws Exception {
        mvc.perform(post("/api/transfers").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString(expectedMessage)));
        verifyNoInteractions(service);
    }

    @Test
    void doesNotExposePersistenceErrors() throws Exception {
        when(service.transfer(1L, new TransferRequest(EMAIL, 1L, null, "key-1")))
                .thenThrow(new DataIntegrityViolationException("SQL constraint detail"));

        mvc.perform(post("/api/transfers").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content(payload("1", "null")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Unable to complete the request"));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("", INVALID_JSON),
                Arguments.of("{", INVALID_JSON),
                Arguments.of("[]", INVALID_JSON),
                Arguments.of("null", INVALID_JSON),
                Arguments.of(payload("1", "null") + " {}", INVALID_JSON),
                Arguments.of("{}", "amount: Amount is required"),
                Arguments.of(payload("null", "null"), "amount: Amount is required"),
                Arguments.of(payload("0", "null"), "amount: Amount must be positive"),
                Arguments.of(payload("-1", "null"), "amount: Amount must be positive"),
                Arguments.of(payload("1.0", "null"), INVALID_JSON),
                Arguments.of(payload("1.5", "null"), INVALID_JSON),
                Arguments.of(payload("1e2", "null"), INVALID_JSON),
                Arguments.of(payload("\"1\"", "null"), INVALID_JSON),
                Arguments.of(payload("true", "null"), INVALID_JSON),
                Arguments.of(payload("9223372036854775808", "null"), INVALID_JSON),
                Arguments.of(payload("1", "12"), INVALID_JSON),
                Arguments.of(payload("1", "{}"), INVALID_JSON),
                Arguments.of(payload("1", "\"\\u0000\""), "description:"),
                Arguments.of(payload("1", "\"" + "x".repeat(501) + "\""), "description:"),
                Arguments.of(payload("1", "null").replace(EMAIL, "invalid-email"), "recipientEmail:"),
                Arguments.of(payload("1", "null").replace(EMAIL, ""), "recipientEmail:"),
                Arguments.of(payload("1", "null").replace("\"" + EMAIL + "\"", "1"), INVALID_JSON),
                Arguments.of(payload("1", "null").replace("\"key-1\"", "null"), "idempotencyKey:"),
                Arguments.of(payload("1", "null").replace("key-1", " "), "idempotencyKey:"),
                Arguments.of(payload("1", "null").replace("key-1", "key/1"), "idempotencyKey:"),
                Arguments.of(payload("1", "null").replace("key-1", "x".repeat(256)), "idempotencyKey:"),
                Arguments.of(payload("1", "null").replace("\"key-1\"", "true"), INVALID_JSON));
    }

    @ParameterizedTest
    @MethodSource("validDescriptions")
    void acceptsOptionalDescriptionAndBoundaryValues(String description, String jsonField) throws Exception {
        String key = "client.v1:_-" + "x".repeat(243);
        when(service.transfer(1L, new TransferRequest(EMAIL, Long.MAX_VALUE, description, key)))
                .thenReturn(new TransferResponse("TRF-TEST", 1L, 2L, Long.MAX_VALUE, "MYR",
                        "COMPLETED", description, OffsetDateTime.now()));

        mvc.perform(post("/api/transfers").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"recipientEmail":"%s","amount":%d,"idempotencyKey":"%s"%s}
                                """).formatted(EMAIL, Long.MAX_VALUE, key, jsonField)))
                .andExpect(status().isCreated());
        verify(service).transfer(1L, new TransferRequest(EMAIL, Long.MAX_VALUE, description, key));
    }

    static Stream<Arguments> validDescriptions() {
        String description = "💸".repeat(500);
        return Stream.of(Arguments.of(null, ""), Arguments.of(null, ",\"description\":null"),
                Arguments.of("", ",\"description\":\"\""),
                Arguments.of(description, ",\"description\":\"" + description + "\""));
    }

    private static String payload(String amount, String description) {
        return """
                {"recipientEmail":"%s","amount":%s,"idempotencyKey":"key-1","description":%s}
                """.formatted(EMAIL, amount, description);
    }
}
