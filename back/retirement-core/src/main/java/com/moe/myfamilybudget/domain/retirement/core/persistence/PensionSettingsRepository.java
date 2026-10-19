package com.moe.myfamilybudget.domain.retirement.core.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository des paramètres du silo Retraite (SILO-220, lot A), sans lien avec {@code BudgetDataEntity}.
 * Branché sur {@code JpaRetirementStore} (silo Retraite, R-40, lot B).
 */
@Repository
public interface PensionSettingsRepository extends JpaRepository<PensionSettingsEntity, Long> {

    Optional<PensionSettingsEntity> findFirstByOrderByIdAsc();
}
