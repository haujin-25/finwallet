package finwallet_api.transfer.dto;

import java.util.List;

public record TransferHistoryPage(
        List<TransferHistoryItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
