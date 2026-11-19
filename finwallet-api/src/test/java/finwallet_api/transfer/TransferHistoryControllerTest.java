package finwallet_api.transfer;

import finwallet_api.common.security.UserPrincipal;
import finwallet_api.transfer.controller.TransferHistoryController;
import finwallet_api.transfer.dto.TransferDirection;
import finwallet_api.transfer.dto.TransferHistoryPage;
import finwallet_api.transfer.service.TransferHistoryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferHistoryControllerTest {

    private final TransferHistoryService historyService = mock(TransferHistoryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var principal = new UserPrincipal(7L, "history@finwallet.test");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        mvc = MockMvcBuilders.standaloneSetup(new TransferHistoryController(historyService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultsToFirstPageAndUsesOnlyThePrincipal() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        when(historyService.history(7L, null, null, null, pageable))
                .thenReturn(new TransferHistoryPage(List.of(), 0, 20, 0, 0, false));

        mvc.perform(get("/api/transfers/me").param("userId", "20").param("walletId", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(historyService).history(7L, null, null, null, pageable);
    }

    @Test
    void bindsOffsetTimestampsAndDirection() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2026-11-18T16:00:00.012345+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-11-18T09:00:00Z");
        PageRequest pageable = PageRequest.of(2, 100);
        when(historyService.history(7L, TransferDirection.INCOMING, from, to, pageable))
                .thenReturn(new TransferHistoryPage(List.of(), 2, 100, 0, 0, false));

        mvc.perform(get("/api/transfers/me").param("direction", "INCOMING")
                        .param("from", from.toString()).param("to", to.toString())
                        .param("page", "2").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        verify(historyService).history(7L, TransferDirection.INCOMING, from, to, pageable);
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1", "page,2147483648", "page,2147483647", "size,0", "size,101", "size,''",
            "direction,incoming", "direction,SIDEWAYS", "from,2026-11-18T10:00:00",
            "from,invalid", "to,2026-11-18T10:00:00.000000001Z"
    })
    void rejectsInvalidParametersBeforeQuerying(String name, String value) throws Exception {
        mvc.perform(get("/api/transfers/me").param(name, value)).andExpect(status().isBadRequest());
        verifyNoInteractions(historyService);
    }

    @Test
    void rejectsEqualInstantsEvenWithDifferentOffsets() throws Exception {
        mvc.perform(get("/api/transfers/me").param("from", "2026-11-18T18:00:00+08:00")
                        .param("to", "2026-11-18T10:00:00Z"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(historyService);
    }
}
