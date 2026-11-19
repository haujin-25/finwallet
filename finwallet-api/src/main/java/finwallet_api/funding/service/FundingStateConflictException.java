package finwallet_api.funding.service;

public class FundingStateConflictException extends RuntimeException {

    public FundingStateConflictException(String message) {
        super(message);
    }
}
