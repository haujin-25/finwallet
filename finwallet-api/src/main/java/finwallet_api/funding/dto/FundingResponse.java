package finwallet_api.funding.dto;

import finwallet_api.funding.entity.FundingOperation;
import finwallet_api.funding.entity.FundingStatus;
import finwallet_api.funding.entity.FundingType;
import java.time.OffsetDateTime;

public record FundingResponse(
        String reference,
        FundingType operationType,
        FundingStatus status,
        long amount,
        String currency,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
    public static FundingResponse from(FundingOperation operation) {
        return new FundingResponse(operation.getReference(), operation.getOperationType(), operation.getStatus(),
                operation.getAmount(), operation.getCurrency(), operation.getDescription(),
                operation.getCreatedAt(), operation.getResolvedAt());
    }
}
