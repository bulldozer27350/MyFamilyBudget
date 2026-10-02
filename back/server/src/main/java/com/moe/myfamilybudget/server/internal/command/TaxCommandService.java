package com.moe.myfamilybudget.server.internal.command;

import java.util.List;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxWriter;

/**
 * Service de commande du domaine Fiscalite (RF-A00, DB-021).
 * Unique point d'ecriture des regles, baremes et parametres fiscaux : valide la commande puis delegue au
 * port {@link TaxWriter}. N'a plus de dependance directe vers {@code PersistenceManager}.
 */
@Service
public class TaxCommandService {

    private final TaxWriter taxWriter;

    public TaxCommandService(TaxWriter taxWriter) {
        this.taxWriter = taxWriter;
    }

    /** Une liste {@code null} conserve la valeur existante (contrat historique de l'API). */
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        taxWriter.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
    }

    /**
     * @throws IllegalArgumentException si {@code field} est {@code null} (aucune ecriture n'est alors faite)
     */
    public void updateTaxSettings(TaxSettingField field, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("Le parametre fiscal est obligatoire");
        }
        taxWriter.updateTaxSettings(field, value);
    }

    public void resetDefaultTaxBrackets() {
        taxWriter.resetDefaultTaxBrackets();
    }
}
