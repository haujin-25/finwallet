package finwallet_api.funding.repository;

import finwallet_api.funding.entity.FundingOperation;
import finwallet_api.funding.entity.FundingType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundingOperationRepository extends JpaRepository<FundingOperation, Long> {

    Optional<FundingOperation> findByWalletIdAndOperationTypeAndIdempotencyKey(
            Long walletId, FundingType type, String key);

    Optional<FundingOperation> findByWalletIdAndReference(Long walletId, String reference);

    @Query("select operation.walletId from FundingOperation operation where operation.reference = :reference")
    Optional<Long> findWalletIdByReference(@Param("reference") String reference);
}
