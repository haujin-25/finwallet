package finwallet_api.reconciliation.service;

import finwallet_api.reconciliation.dto.ReconciliationDiscrepancy;
import finwallet_api.reconciliation.dto.ReconciliationReport;
import finwallet_api.reconciliation.dto.ReconciliationReport.Status;
import finwallet_api.reconciliation.repository.FundingReconciliationRepository;
import finwallet_api.reconciliation.repository.ReconciliationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerReconciliationService {

    private final ReconciliationRepository repository;
    private final FundingReconciliationRepository funding;

    public LedgerReconciliationService(ReconciliationRepository repository, FundingReconciliationRepository funding) {
        this.repository = repository;
        this.funding = funding;
    }

    // Use a committed database snapshot even when invoked from a transactional caller.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public ReconciliationReport reconcile() {
        var snapshot = repository.countRecords();
        var discrepancies = new ArrayList<ReconciliationDiscrepancy>();
        discrepancies.addAll(repository.findBalanceDiscrepancies());
        discrepancies.addAll(repository.findTransferDiscrepancies());
        discrepancies.addAll(repository.findEntryDiscrepancies());
        discrepancies.addAll(repository.findReversalDiscrepancies());
        discrepancies.addAll(funding.findReservationDiscrepancies());
        discrepancies.addAll(funding.findOperationDiscrepancies());
        discrepancies.addAll(funding.findJournalDiscrepancies());
        discrepancies.addAll(funding.findSettlementDiscrepancies());

        return new ReconciliationReport(snapshot.timestamp(), snapshot.wallets(), snapshot.transfers(),
                discrepancies.size(), List.copyOf(discrepancies),
                discrepancies.isEmpty() ? Status.PASS : Status.FAIL);
    }
}
