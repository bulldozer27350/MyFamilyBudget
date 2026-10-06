package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;

/**
 * Service de commande du domaine Import Bancaire / Rapprochement (RF-A00, DB-040).
 * Unique point d'ecriture des transactions bancaires et rapprochements : valide la commande puis delegue
 * au port {@link BankWriter}. N'a plus de dependance directe vers {@code PersistenceManager}.
 *
 * <p>SILO-213 (lot B) : l'import bancaire s'ecrit directement dans sa table ({@code JpaBankStore}), sans passer
 * par le modele global. L'ecriture n'a donc plus d'autre garantie de serialisation et d'atomicite que celles
 * prises ici : elle s'execute dans une transaction du {@link TransactionRunner}, apres la prise du verrou du
 * silo Banque/Pointage ({@link SiloMutationLock}). La synchronisation Enable Banking ecrit par ce meme service
 * (cablage du composition root), donc avec les memes garanties.
 *
 * <p>Un import {@code null} est une erreur de programmation, refuse avant toute ecriture
 * ({@link IllegalArgumentException}, traduite en 400 par le gestionnaire d'erreurs) : aucun appelant
 * actuel n'en transmet.
 */
@Service
public class BankImportCommandService {

    /** Silo ecrit par la mise a jour de l'import bancaire. */
    private static final Set<MutationSilo> UPDATE_SILOS = EnumSet.of(MutationSilo.BANK_POINTAGE);

    private final BankWriter bankWriter;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public BankImportCommandService(BankWriter bankWriter,
                                    SiloMutationLock siloMutationLock,
                                    TransactionRunner transactionRunner) {
        this.bankWriter = bankWriter;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    public void updateBankImport(BankImportModel bankImport) {
        if (bankImport == null) {
            throw new IllegalArgumentException("L'import bancaire est obligatoire");
        }
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(UPDATE_SILOS);
            bankWriter.updateBankImport(bankImport);
            return null;
        });
    }
}
