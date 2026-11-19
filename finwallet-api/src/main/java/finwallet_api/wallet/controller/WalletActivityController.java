package finwallet_api.wallet.controller;

import finwallet_api.common.security.UserPrincipal;
import finwallet_api.wallet.dto.WalletActivityPage;
import finwallet_api.wallet.service.WalletActivityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WalletActivityController {

    private final WalletActivityService activity;

    public WalletActivityController(WalletActivityService activity) {
        this.activity = activity;
    }

    @GetMapping("/api/wallets/me/activity")
    public WalletActivityPage activity(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return activity.forUser(principal.id(), page, size);
    }
}
