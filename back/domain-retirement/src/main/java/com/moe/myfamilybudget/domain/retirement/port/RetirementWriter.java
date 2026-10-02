package com.moe.myfamilybudget.domain.retirement.port;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;

/**
 * Port d'ecriture pour le domaine Retraite (DB-020). Seul le command service du domaine
 * ({@code RetirementCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager}
 * et prepare la bascule JPA (DB-1001) sans changer l'appelant.
 */
public interface RetirementWriter {

    /** Remplace les parametres et les personnes du domaine Retraite. */
    void updateRetirement(RetirementModel retirement);

    /**
     * SET-020 : met à jour un paramètre de la famille Retraite exposé par {@code PATCH /settings}
     * ({@code birthYear}, {@code retireAge}, {@code pass2026}, {@code passGrowthRate}).
     */
    void updateRetirementSetting(RetirementSettingField field, Object value);
}
