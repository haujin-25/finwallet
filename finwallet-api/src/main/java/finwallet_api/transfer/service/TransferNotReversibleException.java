package finwallet_api.transfer.service;

public class TransferNotReversibleException extends RuntimeException {

    public TransferNotReversibleException(String message) {
        super(message);
    }
}
