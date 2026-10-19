package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.retirement.port.RetirementWriter;

/**
 * Service de commande du domaine Retraite (RF-A00, DB-020).
 * Unique point d'ecriture du modele de retraite : valide la commande puis delegue au port
 * {@link RetirementWriter}. N'a plus de dependance directe vers {@code PersistenceManager}.
 *
 * <p>R-40 (lot B, meme gabarit que {@code TaxCommandService}/{@code TresorerieCommandService}) :
 * {@link RetirementWriter} (en pratique {@code JpaRetirementStore}) reecrit integralement
 * {@code pension_plan} ou {@code pension_settings} a chaque mutation (lecture puis
 * {@code deleteAll}/{@code save}), sans transaction englobante ni verrou propre. Chaque ecriture
 * s'execute donc desormais dans une transaction du {@link TransactionRunner}, apres prise du verrou du
 * silo Retraite ({@link SiloMutationLock}), pour eviter les ecritures concurrentes perdues.
 */
@Service
public class RetirementCommandService {

    private static final Set<MutationSilo> RETIREMENT_SILOS = EnumSet.of(MutationSilo.RETIREMENT);

    private final RetirementWriter retirementWriter;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public RetirementCommandService(RetirementWriter retirementWriter, SiloMutationLock siloMutationLock,
                                    TransactionRunner transactionRunner) {
        this.retirementWriter = retirementWriter;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    /**
     * @throws IllegalArgumentException si {@code retirement} est {@code null} (aucune ecriture n'est alors faite)
     */
    public void updateRetirement(RetirementModel retirement) {
        if (retirement == null) {
            throw new IllegalArgumentException("Le modele de retraite est obligatoire");
        }
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(RETIREMENT_SILOS);
            retirementWriter.updateRetirement(retirement);
            return null;
        });
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
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(RETIREMENT_SILOS);
            retirementWriter.updateRetirementSetting(field, value);
            return null;
        });
    }
}
