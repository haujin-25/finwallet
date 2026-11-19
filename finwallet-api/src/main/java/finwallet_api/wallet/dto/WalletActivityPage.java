package finwallet_api.wallet.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record WalletActivityPage(List<Item> items, int page, int size, boolean hasNext) {

    public record Item(String reference, String operationType, String direction, long amount, String currency,
                       String status, String description, OffsetDateTime createdAt) {
    }
}
