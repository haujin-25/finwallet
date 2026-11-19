package finwallet_api.funding.dev;

import finwallet_api.funding.dto.FundingResponse;
import finwallet_api.funding.provider.PaymentProvider.Scenario;
import finwallet_api.funding.service.FundingSettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Conditional(DevFundingEnabled.class)
public class DevFundingController {

    private final FundingSettlementService settlement;

    public DevFundingController(FundingSettlementService settlement) {
        this.settlement = settlement;
    }

    @PostMapping(DevFundingSecurityConfig.SETTLE_PATH)
    public FundingResponse settle(@PathVariable String reference, @Valid @RequestBody SettlementRequest request) {
        return settlement.checkStatus(reference, Scenario.valueOf(request.outcome()));
    }

    public record SettlementRequest(
            @NotBlank @Pattern(regexp = "SUCCESS|FAILURE") String outcome
    ) {}
}
