package finwallet_api.admin.dto;

import java.util.List;

public record AdminAuditPage(List<AdminAuditLog> items, int page, int size, boolean hasNext) {
}
