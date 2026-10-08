package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsWriter;

/**
 * Owner applicatif de la notion Hypothèses économiques (SET-020, voir doc/architecture/12-settings.md) :
 * taux d'inflation ({@code inflationRate}). Ce n'est pas un domaine Settings global : une seule propriété.
 *
 * <p>R-50 : le paramètre s'écrit directement dans {@code app_settings} (silo Paramètres, DA-06), sans passer par le
 * modèle global. L'écriture s'exécute donc dans une transaction du {@link TransactionRunner}, après la prise du
 * verrou du silo Paramètres ({@link SiloMutationLock}), partagé avec la simulation (même ligne).
 */
@Service
public class EconomicAssumptionsCommandService {

    private static final Set<MutationSilo> SETTINGS_SILOS = EnumSet.of(MutationSilo.SETTINGS);

    private final EconomicAssumptionsWriter writer;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public EconomicAssumptionsCommandService(EconomicAssumptionsWriter writer,
                                             SiloMutationLock siloMutationLock,
                                             TransactionRunner transactionRunner) {
        this.writer = writer;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    public void updateInflationRate(Object value) {
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(SETTINGS_SILOS);
            writer.updateInflationRate(value);
            return null;
        });
    }
}
