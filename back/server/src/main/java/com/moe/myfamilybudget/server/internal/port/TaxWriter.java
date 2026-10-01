package com.moe.myfamilybudget.server.internal.port;

import java.util.List;

import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;

/**
 * Port d'ecriture pour le domaine Fiscalite (DB-021). Seul le command service du domaine
 * ({@code TaxCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et prepare
 * la bascule JPA (DB-1011) sans changer l'appelant.
 */
public interface TaxWriter {

    /** Met a jour les listes fournies ; une liste {@code null} conserve la valeur existante. */
    void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                         List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides);

    /** Met a jour un champ des parametres historiquement portes par la fiscalite (voir 12-settings.md). */
    void updateTaxSettings(String field, Object value);

    /** Restaure le bareme par defaut ; enfants et surcharges sont conserves. */
    void resetDefaultTaxBrackets();

    /**
     * VT-350b : prend le verrou de mutation du budget pour la transaction en cours. Responsabilite
     * transverse, a redistribuer par DB-061.
     */
    void lockBudgetForCurrentTransaction();
}
