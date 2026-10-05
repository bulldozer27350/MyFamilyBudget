package com.moe.myfamilybudget.domain.tax.core.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Repository autonome du domaine Fiscalite (DB-1010), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code TaxPersistenceAdapter} (bascule en DB-1011).
 */
@Repository
public interface FiscalRateOverrideRepository extends JpaRepository<FiscalRateOverrideEntity, Long> {

    List<FiscalRateOverrideEntity> findAllByOrderByPositionAsc();
}
