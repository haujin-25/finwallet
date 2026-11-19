package finwallet_api.transfer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

public record TransferHistoryItem(
        String transferReference,
        String direction,
        Long amount,
        String currency,
        String status,
        String description,
        String counterpartyName,
        OffsetDateTime createdAt,
        String operationType,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reversalOfReference
) {
}
