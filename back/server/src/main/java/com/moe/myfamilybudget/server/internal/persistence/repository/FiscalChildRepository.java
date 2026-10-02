package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.FiscalChildEntity;

/**
 * Repository autonome du domaine Fiscalite (DB-1010), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code TaxPersistenceAdapter} (bascule en DB-1011).
 */
@Repository
public interface FiscalChildRepository extends JpaRepository<FiscalChildEntity, Long> {

    List<FiscalChildEntity> findAllByOrderByPositionAsc();
}
