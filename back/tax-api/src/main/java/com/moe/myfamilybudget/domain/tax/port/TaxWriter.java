package com.moe.myfamilybudget.domain.tax.port;

import java.util.List;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;

/**
 * Port d'ecriture pour le domaine Fiscalite (DB-021). Seul le command service du domaine
 * ({@code TaxCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et prepare
 * la bascule JPA (DB-1011) sans changer l'appelant.
 */
public interface TaxWriter {

    /** Met a jour les listes fournies ; une liste {@code null} conserve la valeur existante. */
    void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                         List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides);

    /** Met a jour un parametre dont Fiscalite est owner ({@code childExitAge}, {@code taxAbattement}). */
    void updateTaxSettings(TaxSettingField field, Object value);

    /** Restaure le bareme par defaut ; enfants et surcharges sont conserves. */
    void resetDefaultTaxBrackets();
}
