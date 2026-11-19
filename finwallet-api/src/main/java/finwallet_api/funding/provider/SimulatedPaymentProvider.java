package finwallet_api.funding.provider;

import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public Confirmation verify(String transactionReference, Scenario scenario) {
        // This adapter only simulates a status check. It never initiates a payment.
        String eventReference = scenario == Scenario.TIMEOUT ? null : transactionReference + ":" + scenario;
        return new Confirmation(transactionReference, eventReference, scenario);
    }
}
