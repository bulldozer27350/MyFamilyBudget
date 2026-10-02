package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.FiscalActualOverrideEntity;

/**
 * Repository autonome du domaine Fiscalite (DB-1010), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code TaxPersistenceAdapter} (bascule en DB-1011).
 */
@Repository
public interface FiscalActualOverrideRepository extends JpaRepository<FiscalActualOverrideEntity, Long> {

    List<FiscalActualOverrideEntity> findAllByOrderByPositionAsc();
}
