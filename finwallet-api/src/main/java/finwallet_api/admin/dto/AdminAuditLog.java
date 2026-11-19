package finwallet_api.admin.dto;

import java.time.OffsetDateTime;

public record AdminAuditLog(long id, long administratorUserId, Action action, long walletId,
                            OffsetDateTime occurredAt, String reason, String previousStatus,
                            String newStatus, Outcome outcome) {

    public enum Action {
        FREEZE,
        UNFREEZE
    }

    public enum Outcome {
        APPLIED,
        NO_CHANGE
    }
}
