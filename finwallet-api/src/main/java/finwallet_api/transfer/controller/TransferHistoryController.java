package finwallet_api.transfer.controller;

import finwallet_api.common.security.UserPrincipal;
import finwallet_api.transfer.dto.TransferDirection;
import finwallet_api.transfer.dto.TransferHistoryPage;
import finwallet_api.transfer.service.TransferHistoryService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TransferHistoryController {

    private final TransferHistoryService historyService;

    public TransferHistoryController(TransferHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/api/transfers/me")
    public TransferHistoryPage history(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        int pageNumber = integerParameter(page, 0, "page");
        int pageSize = integerParameter(size, 20, "size");
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100
                || (long) pageNumber * pageSize > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }

        OffsetDateTime fromInclusive = timestamp(from, "from");
        OffsetDateTime toExclusive = timestamp(to, "to");
        if (fromInclusive != null && toExclusive != null && !fromInclusive.isBefore(toExclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }

        return historyService.history(principal.id(), direction(direction), fromInclusive, toExclusive,
                PageRequest.of(pageNumber, pageSize));
    }

    private static int integerParameter(String value, int defaultValue, String name) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be an integer");
        }
    }

    private static TransferDirection direction(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TransferDirection.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction must be INCOMING or OUTGOING");
        }
    }

    private static OffsetDateTime timestamp(String value, String name) {
        if (value == null) {
            return null;
        }
        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(value);
            // Match PostgreSQL timestamp precision and avoid silently rounding filter boundaries.
            if (timestamp.getYear() >= 1 && timestamp.getYear() <= 9999 && timestamp.getNano() % 1000 == 0) {
                return timestamp;
            }
        } catch (DateTimeParseException exception) {
            throw invalidTimestamp(name);
        }
        throw invalidTimestamp(name);
    }

    private static ResponseStatusException invalidTimestamp(String name) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                name + " must be an ISO-8601 timestamp with a timezone offset and microsecond precision");
    }
}
