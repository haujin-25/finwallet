package finwallet_api.funding.dev;

import finwallet_api.common.exception.GlobalExceptionHandler;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.service.FundingSettlementService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevFundingSecurityTest {

    private static final String TOKEN = "a-local-dev-token-for-test-only-123";
    private static final String PATH = "/api/dev/funding/DEP-test/settle";
    private final FundingSettlementService settlement = mock(FundingSettlementService.class);
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(WebSecurityTestConfig.class, DevFundingSecurityConfig.class,
                    DevFundingController.class, GlobalExceptionHandler.class)
            .withBean(FundingSettlementService.class, () -> settlement);

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production", "local,production", "test,staging", "staging"})
    void enabledFlagCannotRegisterTheEndpointWithNonDevelopmentProfiles(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles, "finwallet.dev.funding.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DevFundingController.class);
                    assertThat(context).doesNotHaveBean("devFundingSecurityFilterChain");
                    MockMvcBuilders.webAppContextSetup(context).build().perform(post(PATH))
                            .andExpect(status().isNotFound());
                });
    }

    @Test
    void localProfileAloneDoesNotEnableTheEndpoint() {
        runner.withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DevFundingController.class);
                    assertThat(context).doesNotHaveBean("devFundingSecurityFilterChain");
                });
    }

    @Test
    void enabledFlagNeedsAnExplicitDevelopmentProfile() {
        runner.withPropertyValues("spring.profiles.default=local", "finwallet.dev.funding.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(DevFundingController.class));
    }

    @Test
    void missingTokenConfigurationFailsClosed() {
        runner.withPropertyValues("spring.profiles.active=local", "finwallet.dev.funding.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(DevFundingController.class);
                    MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                            .perform(post(PATH).header("X-Dev-Funding-Token", TOKEN))
                            .andExpect(status().isForbidden());
                    verifyNoInteractions(settlement);
                });
    }

    @Test
    void requiresTheDevTokenAndALoopbackConnection() {
        runner.withPropertyValues("spring.profiles.active=local", "finwallet.dev.funding.enabled=true",
                        "finwallet.dev.funding.token=" + TOKEN)
                .run(context -> {
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
                    mvc.perform(post(PATH)).andExpect(status().isForbidden());
                    mvc.perform(post(PATH).header("X-Dev-Funding-Token", "wrong"))
                            .andExpect(status().isForbidden());
                    mvc.perform(post(PATH).header("X-Dev-Funding-Token", TOKEN).with(request -> {
                        request.setRemoteAddr("192.0.2.10");
                        request.addHeader("X-Forwarded-For", "127.0.0.1");
                        return request;
                    })).andExpect(status().isForbidden());
                    mvc.perform(post(PATH).header("X-Dev-Funding-Token", TOKEN)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"TIMEOUT\"}"))
                            .andExpect(status().isBadRequest());
                    verifyNoInteractions(settlement);

                    var now = OffsetDateTime.now();
                    when(settlement.checkStatus("DEP-test", Scenario.SUCCESS)).thenReturn(new FundingResponse(
                            "DEP-test", FundingType.DEPOSIT, FundingStatus.COMPLETED, 10000, "MYR", null, now, now));
                    mvc.perform(post(PATH).header("X-Dev-Funding-Token", TOKEN)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("COMPLETED"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class WebSecurityTestConfig {
    }
}
