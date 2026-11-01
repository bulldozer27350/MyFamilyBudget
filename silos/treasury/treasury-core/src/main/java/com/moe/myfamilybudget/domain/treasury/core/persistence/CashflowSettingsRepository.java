package com.moe.myfamilybudget.domain.treasury.core.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository des paramètres du silo Trésorerie (SILO-220, lot A), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branché sur un adaptateur (bascule au lot B).
 */
@Repository
public interface CashflowSettingsRepository extends JpaRepository<CashflowSettingsEntity, Long> {

    Optional<CashflowSettingsEntity> findFirstByOrderByIdAsc();
}
