package com.moe.myfamilybudget.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.persistence.entity.FiscalBracketEntity;

/**
 * Repository autonome du domaine Fiscalite (DB-1010), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code TaxPersistenceAdapter} (bascule en DB-1011).
 */
@Repository
public interface FiscalBracketRepository extends JpaRepository<FiscalBracketEntity, Long> {

    List<FiscalBracketEntity> findAllByOrderByPositionAsc();
}
