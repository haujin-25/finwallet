package finwallet_api.transfer.risk;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferRiskAdminController {

    private final TransferRiskRepository repository;

    public TransferRiskAdminController(TransferRiskRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/admin/transfer-risk-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public AlertPage alerts(@RequestParam(defaultValue = "0") @PositiveOrZero int page,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var rows = repository.readPage((long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        return new AlertPage(hasNext ? List.copyOf(rows.subList(0, size)) : rows, page, size, hasNext);
    }

    public record AlertPage(List<TransferRiskRepository.Alert> items, int page, int size, boolean hasNext) {}
}
