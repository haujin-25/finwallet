package finwallet_api.transfer.risk;

import finwallet_api.transfer.entity.Transfer;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

import static finwallet_api.transfer.risk.TransferLimitException.Code.DAILY_TRANSFER_AMOUNT_LIMIT;
import static finwallet_api.transfer.risk.TransferLimitException.Code.DAILY_TRANSFER_COUNT_LIMIT;
import static finwallet_api.transfer.risk.TransferLimitException.Code.TRANSFER_AMOUNT_LIMIT;

@Service
public class TransferPolicy {

    private final TransferPolicyProperties settings;
    private final TransferRiskRepository repository;
    private final ZoneId zone;

    public TransferPolicy(TransferPolicyProperties settings, TransferRiskRepository repository) {
        this.settings = settings;
        this.repository = repository;
        this.zone = ZoneId.of(settings.dailyZone());
    }

    // Called only for a new customer transfer while holding the sender wallet write lock.
    public List<TransferRiskRule> evaluate(Transfer transfer) {
        long amount = transfer.getAmount();
        if (settings.maxAmountSen() != null && amount > settings.maxAmountSen()) {
            throw new TransferLimitException(TRANSFER_AMOUNT_LIMIT);
        }
        enforceDailyLimits(transfer);

        var alerts = new ArrayList<TransferRiskRule>();
        if (settings.riskLargeAmountSen() != null && amount >= settings.riskLargeAmountSen()) {
            alerts.add(TransferRiskRule.LARGE_TRANSFER);
        }
        if (settings.riskRapidCount() != null || settings.riskDistinctRecipients() != null) {
            OffsetDateTime now = transfer.getCreatedAt();
            var recent = repository.recentUsage(transfer.getSenderWalletId(), transfer.getRecipientWalletId(),
                    now.minus(settings.riskWindow()), now);
            if (settings.riskRapidCount() != null && recent.count() >= settings.riskRapidCount() - 1) {
                alerts.add(TransferRiskRule.RAPID_TRANSFERS);
            }
            if (settings.riskDistinctRecipients() != null
                    && recent.recipients() >= settings.riskDistinctRecipients() - (recent.recipientSeen() ? 0 : 1)) {
                alerts.add(TransferRiskRule.DISTINCT_RECIPIENTS);
            }
        }
        return alerts;
    }

    private void enforceDailyLimits(Transfer transfer) {
        if (settings.dailyMaxAmountSen() == null && settings.dailyMaxCount() == null) {
            return;
        }
        var day = transfer.getCreatedAt().atZoneSameInstant(zone).toLocalDate();
        var start = day.atStartOfDay(zone).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        var end = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        var usage = repository.dailyUsage(transfer.getSenderWalletId(), start, end);

        if (settings.dailyMaxAmountSen() != null
                && usage.amount().add(BigInteger.valueOf(transfer.getAmount()))
                        .compareTo(BigInteger.valueOf(settings.dailyMaxAmountSen())) > 0) {
            throw new TransferLimitException(DAILY_TRANSFER_AMOUNT_LIMIT);
        }
        if (settings.dailyMaxCount() != null && usage.count() >= settings.dailyMaxCount()) {
            throw new TransferLimitException(DAILY_TRANSFER_COUNT_LIMIT);
        }
    }

    public void record(Transfer posted, List<TransferRiskRule> rules) {
        if (!rules.isEmpty()) {
            repository.append(posted, rules);
        }
    }
}
