package finwallet_api.funding.provider;

public interface PaymentProvider {

    Confirmation verify(String transactionReference, Scenario scenario);

    enum Scenario {
        SUCCESS,
        FAILURE,
        TIMEOUT
    }

    record Confirmation(String transactionReference, String eventReference, Scenario outcome) {
    }
}
