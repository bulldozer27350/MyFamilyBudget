package com.moe.myfamilybudget.domain.retirement.core.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Repository autonome du domaine Retraite (DB-1000), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code RetirementPersistenceAdapter} (bascule en DB-1001).
 */
@Repository
public interface PensionPlanRepository extends JpaRepository<PensionPlanEntity, Long> {

    Optional<PensionPlanEntity> findFirstByOrderByIdAsc();
}
