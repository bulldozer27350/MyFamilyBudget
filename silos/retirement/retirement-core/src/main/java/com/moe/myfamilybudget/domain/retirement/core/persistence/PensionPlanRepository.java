package com.moe.myfamilybudget.domain.retirement.core.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Repository autonome du domaine Retraite (DB-1000), sans lien avec {@code BudgetDataEntity}.
 * Branche sur {@code JpaRetirementStore} (silo Retraite, DB-1001, R-40).
 */
@Repository
public interface PensionPlanRepository extends JpaRepository<PensionPlanEntity, Long> {

    Optional<PensionPlanEntity> findFirstByOrderByIdAsc();
}
