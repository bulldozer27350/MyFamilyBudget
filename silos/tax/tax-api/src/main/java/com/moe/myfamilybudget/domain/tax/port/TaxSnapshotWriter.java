package com.moe.myfamilybudget.domain.tax.port;

import java.util.List;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;

/**
 * Port d'import et de réinitialisation du silo Fiscalité (SILO-119, lot B1). L'export passe par
 * {@link TaxReader} et {@link TaxSettingsReader}.
 *
 * <p>Ces opérations ne s'appellent que dans une transaction déjà ouverte et après la prise du verrou de
 * mutation. Elles ne modifient que les données du silo Fiscalité.
 */
public interface TaxSnapshotWriter {

    /** Remplace les paramètres et la configuration fiscale ; une liste {@code null} est lue comme vide. */
    void replace(TaxSettingsModel settings, List<TaxChildModel> children, List<TaxBracketModel> brackets,
                 List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides);

    /** Remet le silo Fiscalité à ses valeurs par défaut (barème par défaut, aucune surcharge). */
    void reset();
}
