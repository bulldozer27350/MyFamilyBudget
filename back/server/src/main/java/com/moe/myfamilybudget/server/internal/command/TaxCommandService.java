package com.moe.myfamilybudget.server.internal.command;

import java.util.List;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service de commande du domaine Fiscalite (RF-A00).
 * Encapsule les operations d'ecriture sur les regles, baremes et parametres fiscaux.
 */
@Service
public class TaxCommandService {

    private final PersistenceManager persistenceManager;

    public TaxCommandService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        persistenceManager.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
    }

    /**
     * VT-350b : à appeler en premier par une façade transactionnelle multi-domaines (paramètres),
     * avant toute écriture Objectifs, pour que le verrou du budget soit toujours pris avant les
     * verrous de lignes de la base (pas d'interblocage entre deux façades).
     */
    public void lockBudgetForCurrentTransaction() {
        persistenceManager.lockForCurrentTransaction();
    }

    public void updateTaxSettings(String field, Object value) {
        persistenceManager.updateTaxSettings(field, value);
    }

    public void resetDefaultTaxBrackets() {
        persistenceManager.resetDefaultTaxBrackets();
    }
}