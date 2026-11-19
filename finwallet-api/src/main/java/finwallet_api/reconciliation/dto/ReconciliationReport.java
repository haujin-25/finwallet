package finwallet_api.reconciliation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReconciliationReport(
        OffsetDateTime reconciledAt,
        long walletsChecked,
        long transfersChecked,
        int discrepancyCount,
        List<ReconciliationDiscrepancy> discrepancies,
        Status status
) {
    public enum Status {
        PASS,
        FAIL
    }
}
