package finwallet_api.transfer.risk;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferPostingTime {

    private final JdbcTemplate jdbc;

    public TransferPostingTime(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OffsetDateTime now() {
        // Read wall-clock time after locking. CURRENT_TIMESTAMP is fixed at transaction start.
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class)
                .withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
