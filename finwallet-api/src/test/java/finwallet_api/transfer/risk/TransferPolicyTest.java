package finwallet_api.transfer.risk;

import finwallet_api.transfer.entity.Transfer;
import java.math.BigInteger;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static finwallet_api.transfer.risk.TransferLimitException.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransferPolicyTest {

    private final TransferRiskRepository repository = mock(TransferRiskRepository.class);
    private final OffsetDateTime instant = OffsetDateTime.parse("2026-11-20T16:00:00Z");

    @Test
    void absentThresholdsPreserveExistingBehaviorWithoutQueryingUsage() {
        assertThat(policy(null, null, null, null, null, null).evaluate(transfer(Long.MAX_VALUE))).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void doesExactArithmeticAndUsesKualaLumpurCalendarDayBounds() {
        var start = instant;
        var end = instant.plusDays(1);
        when(repository.dailyUsage(1L, start, end)).thenReturn(
                new TransferRiskRepository.DailyUsage(BigInteger.valueOf(Long.MAX_VALUE - 1), 1));
        var policy = policy(null, Long.MAX_VALUE, null, null, null, null);

        assertThat(policy.evaluate(transfer(1L))).isEmpty();
        assertThatThrownBy(() -> policy.evaluate(transfer(2L)))
                .isInstanceOfSatisfying(TransferLimitException.class,
                        error -> assertThat(error.code()).isEqualTo(DAILY_TRANSFER_AMOUNT_LIMIT));
    }

    @Test
    void calendarDayBoundariesRespectDaylightSaving() {
        var time = OffsetDateTime.parse("2026-03-08T15:00:00Z");
        var start = OffsetDateTime.parse("2026-03-08T05:00:00Z");
        var end = OffsetDateTime.parse("2026-03-09T04:00:00Z");
        when(repository.dailyUsage(1L, start, end)).thenReturn(
                new TransferRiskRepository.DailyUsage(BigInteger.ZERO, 0));
        var policy = new TransferPolicy(new TransferPolicyProperties(null, 10L, null,
                "America/New_York", null, null, null, Duration.ofMinutes(5)), repository);

        assertThat(policy.evaluate(new Transfer("TRF-test", 1L, 2L, 10L, "MYR", null, "key", time))).isEmpty();
    }

    @Test
    void countsTheCandidateAndDoesNotCountTheSameRecipientTwice() {
        var policy = policy(null, null, null, 100L, 2L, 2L);
        when(repository.recentUsage(1L, 2L, instant.minusMinutes(5), instant)).thenReturn(
                new TransferRiskRepository.RecentUsage(1, 1, true),
                new TransferRiskRepository.RecentUsage(1, 1, false));

        assertThat(policy.evaluate(transfer(100L)))
                .isEqualTo(List.of(TransferRiskRule.LARGE_TRANSFER, TransferRiskRule.RAPID_TRANSFERS));
        assertThat(policy.evaluate(transfer(100L)))
                .isEqualTo(List.of(TransferRiskRule.LARGE_TRANSFER, TransferRiskRule.RAPID_TRANSFERS,
                        TransferRiskRule.DISTINCT_RECIPIENTS));
    }

    @Test
    void rejectsInvalidThresholdsAndRiskWindows() {
        assertThatThrownBy(() -> policy(0L, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransferPolicyProperties(null, null, null, "UTC",
                null, null, null, Duration.ofDays(2))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bindsDefaultsAndConfiguredThresholds() {
        var runner = new ApplicationContextRunner().withUserConfiguration(Config.class);
        runner.run(context -> {
            var settings = context.getBean(TransferPolicyProperties.class);
            assertThat(settings.dailyZone()).isEqualTo("Asia/Kuala_Lumpur");
            assertThat(settings.riskWindow()).isEqualTo(Duration.ofMinutes(5));
            assertThat(settings.dailyMaxAmountSen()).isNull();
        });
        runner.withPropertyValues("finwallet.transfer.daily-max-amount-sen=500",
                        "finwallet.transfer.risk-window=PT30S")
                .run(context -> {
                    var settings = context.getBean(TransferPolicyProperties.class);
                    assertThat(settings.dailyMaxAmountSen()).isEqualTo(500);
                    assertThat(settings.riskWindow()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TransferPolicyProperties.class)
    static class Config {}

    private TransferPolicy policy(Long maxAmount, Long dailyAmount, Long dailyCount,
                                  Long large, Long rapid, Long recipients) {
        return new TransferPolicy(new TransferPolicyProperties(maxAmount, dailyAmount, dailyCount,
                "Asia/Kuala_Lumpur", large, rapid, recipients, Duration.ofMinutes(5)), repository);
    }

    private Transfer transfer(long amount) {
        return new Transfer("TRF-test", 1L, 2L, amount, "MYR", null, "key", instant);
    }
}
