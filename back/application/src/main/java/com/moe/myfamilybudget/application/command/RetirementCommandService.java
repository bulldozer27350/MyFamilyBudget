package com.moe.myfamilybudget.application.command;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.retirement.port.RetirementWriter;

/**
 * Service de commande du domaine Retraite (RF-A00, DB-020).
 * Unique point d'ecriture du modele de retraite : valide la commande puis delegue au port
 * {@link RetirementWriter}. N'a plus de dependance directe vers {@code PersistenceManager}.
 */
@Service
public class RetirementCommandService {

    private final RetirementWriter retirementWriter;

    public RetirementCommandService(RetirementWriter retirementWriter) {
        this.retirementWriter = retirementWriter;
    }

    /**
     * @throws IllegalArgumentException si {@code retirement} est {@code null} (aucune ecriture n'est alors faite)
     */
    public void updateRetirement(RetirementModel retirement) {
        if (retirement == null) {
            throw new IllegalArgumentException("Le modele de retraite est obligatoire");
        }
        retirementWriter.updateRetirement(retirement);
    }

    /**
     * SET-020 : owner des paramètres de la famille Retraite exposés par {@code PATCH /settings}.
     *
     * @throws IllegalArgumentException si {@code field} est {@code null} (aucune ecriture n'est alors faite)
     */
    public void updateRetirementSetting(RetirementSettingField field, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("Le parametre de retraite est obligatoire");
        }
        retirementWriter.updateRetirementSetting(field, value);
    }
}
