package finwallet_api.admin.controller;

import finwallet_api.admin.dto.AdminAuditPage;
import finwallet_api.admin.dto.AdminWalletResponse;
import finwallet_api.admin.dto.WalletAdministrationRequest;
import finwallet_api.admin.service.WalletAdministrationService;
import finwallet_api.reconciliation.dto.ReconciliationReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class WalletAdministrationController {

    private final WalletAdministrationService administration;

    public WalletAdministrationController(WalletAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/wallets/{walletId}")
    public AdminWalletResponse getWallet(@PathVariable @Positive long walletId) {
        return administration.getWallet(walletId);
    }

    @PostMapping("/wallets/{walletId}/freeze")
    public AdminWalletResponse freeze(@PathVariable @Positive long walletId,
                                      @Valid @RequestBody WalletAdministrationRequest request) {
        return administration.freeze(walletId, request);
    }

    @PostMapping("/wallets/{walletId}/unfreeze")
    public AdminWalletResponse unfreeze(@PathVariable @Positive long walletId,
                                        @Valid @RequestBody WalletAdministrationRequest request) {
        return administration.unfreeze(walletId, request);
    }

    @GetMapping("/audit-logs")
    public AdminAuditPage auditLogs(@RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return administration.auditLogs(page, size);
    }

    @GetMapping("/reconciliation")
    public ReconciliationReport reconcile() {
        return administration.reconcile();
    }
}
