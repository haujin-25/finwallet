package finwallet_api.admin.service;

import finwallet_api.admin.dto.AdminAuditLog;
import finwallet_api.admin.dto.AdminAuditLog.Action;
import finwallet_api.admin.dto.AdminAuditLog.Outcome;
import finwallet_api.admin.dto.AdminAuditPage;
import finwallet_api.admin.dto.AdminWalletResponse;
import finwallet_api.admin.dto.WalletAdministrationRequest;
import finwallet_api.admin.repository.AdminAuditRepository;
import finwallet_api.common.exception.InactiveWalletException;
import finwallet_api.common.exception.ResourceNotFoundException;
import finwallet_api.common.security.UserPrincipal;
import finwallet_api.reconciliation.dto.ReconciliationReport;
import finwallet_api.reconciliation.service.LedgerReconciliationService;
import finwallet_api.wallet.entity.Wallet;
import finwallet_api.wallet.repository.WalletRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class WalletAdministrationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String FROZEN = "FROZEN";

    private final WalletRepository wallets;
    private final AdminAuditRepository audit;
    private final LedgerReconciliationService reconciliation;

    public WalletAdministrationService(WalletRepository wallets, AdminAuditRepository audit,
                                       LedgerReconciliationService reconciliation) {
        this.wallets = wallets;
        this.audit = audit;
        this.reconciliation = reconciliation;
    }

    @Transactional(readOnly = true)
    public AdminWalletResponse getWallet(@Positive long walletId) {
        return wallets.findById(walletId).map(AdminWalletResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }

    // Use a fresh persistence context so the status is first read under the wallet lock.
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public AdminWalletResponse freeze(@Positive long walletId, @NotNull @Valid WalletAdministrationRequest request) {
        return transition(walletId, request.reason(), Action.FREEZE, FROZEN);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public AdminWalletResponse unfreeze(@Positive long walletId, @NotNull @Valid WalletAdministrationRequest request) {
        return transition(walletId, request.reason(), Action.UNFREEZE, ACTIVE);
    }

    @Transactional(readOnly = true)
    public AdminAuditPage auditLogs(@PositiveOrZero int page, @Min(1) @Max(100) int size) {
        List<AdminAuditLog> entries = audit.findPage((long) page * size, size + 1);
        boolean hasNext = entries.size() > size;
        return new AdminAuditPage(hasNext ? List.copyOf(entries.subList(0, size)) : List.copyOf(entries),
                page, size, hasNext);
    }

    public ReconciliationReport reconcile() {
        return reconciliation.reconcile();
    }

    private AdminWalletResponse transition(long walletId, String reason, Action action, String target) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal actor)) {
            throw new AccessDeniedException("Authenticated administrator identity required");
        }

        Wallet wallet = wallets.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        String previous = wallet.getStatus();
        if (!ACTIVE.equals(previous) && !FROZEN.equals(previous)) {
            throw new InactiveWalletException("Only active or frozen wallets can change freeze status");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean changed = !target.equals(previous);
        if (changed) {
            wallet.setStatus(target);
            wallet.setUpdatedAt(now);
        }
        audit.append(actor.id(), action, walletId, now, reason, previous, target,
                changed ? Outcome.APPLIED : Outcome.NO_CHANGE);
        return AdminWalletResponse.from(wallet);
    }
}
