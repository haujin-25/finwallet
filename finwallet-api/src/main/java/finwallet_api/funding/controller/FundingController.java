package finwallet_api.funding.controller;

import finwallet_api.common.security.UserPrincipal;
import finwallet_api.funding.dto.FundingRequest;
import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.entity.FundingType;
import finwallet_api.funding.service.FundingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FundingController {

    private final FundingService funding;

    public FundingController(FundingService funding) {
        this.funding = funding;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public FundingResponse deposit(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody FundingRequest request) {
        return funding.request(principal.id(), FundingType.DEPOSIT, request);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public FundingResponse withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody FundingRequest request) {
        return funding.request(principal.id(), FundingType.WITHDRAWAL, request);
    }

    @GetMapping("/funding/{reference}")
    public FundingResponse status(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String reference) {
        return funding.get(principal.id(), reference);
    }
}
