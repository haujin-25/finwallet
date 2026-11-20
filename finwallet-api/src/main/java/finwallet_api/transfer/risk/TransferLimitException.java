package finwallet_api.transfer.risk;

public class TransferLimitException extends RuntimeException {

    public enum Code {
        TRANSFER_AMOUNT_LIMIT,
        DAILY_TRANSFER_AMOUNT_LIMIT,
        DAILY_TRANSFER_COUNT_LIMIT
    }

    private final Code code;

    public TransferLimitException(Code code) {
        super(switch (code) {
            case TRANSFER_AMOUNT_LIMIT -> "The transfer exceeds the per-transfer amount limit";
            case DAILY_TRANSFER_AMOUNT_LIMIT -> "The transfer exceeds the daily outgoing amount limit";
            case DAILY_TRANSFER_COUNT_LIMIT -> "The daily outgoing transfer count limit has been reached";
        });
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
