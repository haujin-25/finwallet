package finwallet_api.transfer.risk;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("finwallet.transfer")
public record TransferPolicyProperties(
        Long maxAmountSen,
        Long dailyMaxAmountSen,
        Long dailyMaxCount,
        @DefaultValue("Asia/Kuala_Lumpur") String dailyZone,
        Long riskLargeAmountSen,
        Long riskRapidCount,
        Long riskDistinctRecipients,
        @DefaultValue("PT5M") Duration riskWindow
) {
    public TransferPolicyProperties {
        positive("max-amount-sen", maxAmountSen);
        positive("daily-max-amount-sen", dailyMaxAmountSen);
        positive("daily-max-count", dailyMaxCount);
        positive("risk-large-amount-sen", riskLargeAmountSen);
        positive("risk-rapid-count", riskRapidCount);
        positive("risk-distinct-recipients", riskDistinctRecipients);
        ZoneId.of(dailyZone);
        if (riskWindow.compareTo(Duration.ofSeconds(1)) < 0
                || riskWindow.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("risk-window must be between one second and 24 hours");
        }
    }

    private static void positive(String name, Long value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive when configured");
        }
    }
}
