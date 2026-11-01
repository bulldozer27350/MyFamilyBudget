package com.moe.myfamilybudget.domain.settings.core.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository des paramètres du silo Paramètres (SILO-220, lot A2), sans lien avec {@code BudgetDataEntity}.
 * Utilisé par {@link JpaAppSettingsStore} (R-50).
 */
@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettingsEntity, Long> {

    Optional<AppSettingsEntity> findFirstByOrderByIdAsc();
}
