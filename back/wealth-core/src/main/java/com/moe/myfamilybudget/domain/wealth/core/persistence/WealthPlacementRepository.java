package com.moe.myfamilybudget.domain.wealth.core.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository autonome du domaine Patrimoine (DB-1050), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code PatrimoinePersistenceAdapter} (bascule en DB-1051).
 */
@Repository
public interface WealthPlacementRepository extends JpaRepository<WealthPlacementEntity, Long> {

    List<WealthPlacementEntity> findAllByOrderByPositionAsc();
}
