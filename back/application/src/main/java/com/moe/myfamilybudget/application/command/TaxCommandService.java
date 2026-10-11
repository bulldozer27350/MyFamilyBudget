package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
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
 *
 * <p>R-42 (lot B, meme gabarit que {@code TresorerieCommandService} / {@code SimulationSettingsCommandService}) :
 * {@link TaxWriter} (en pratique {@code JpaTaxStore}) reecrit la liste entiere de chaque mutation (lecture,
 * puis {@code deleteAll}/{@code saveAll}) sans transaction englobante ni verrou propre. Chaque ecriture
 * s'execute donc desormais dans une transaction du {@link TransactionRunner}, apres prise du verrou du silo
 * Fiscalite ({@link SiloMutationLock}).
 */
@Service
public class TaxCommandService {

    private static final Set<MutationSilo> TAX_SILOS = EnumSet.of(MutationSilo.TAX);

    private final TaxWriter taxWriter;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public TaxCommandService(TaxWriter taxWriter,
                             SiloMutationLock siloMutationLock,
                             TransactionRunner transactionRunner) {
        this.taxWriter = taxWriter;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    /** Une liste {@code null} conserve la valeur existante (contrat historique de l'API). */
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(TAX_SILOS);
            taxWriter.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
            return null;
        });
    }

    /**
     * @throws IllegalArgumentException si {@code field} est {@code null} (aucune ecriture n'est alors faite)
     */
    public void updateTaxSettings(TaxSettingField field, Object value) {
        if (field == null) {
            throw new IllegalArgumentException("Le parametre fiscal est obligatoire");
        }
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(TAX_SILOS);
            taxWriter.updateTaxSettings(field, value);
            return null;
        });
    }

    public void resetDefaultTaxBrackets() {
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(TAX_SILOS);
            taxWriter.resetDefaultTaxBrackets();
            return null;
        });
    }
}
