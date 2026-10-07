package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.credit.port.LoanWriter;

/**
 * Service de commande du domaine Prets/Emprunts (RF-A00, DB-041).
 * Unique point d'ecriture des prets : valide la commande puis delegue au port {@link LoanWriter}. Ne
 * depend plus de {@link PatrimoineCommandService} ni de {@code PersistenceManager}.
 *
 * <p>SILO-214 (lot B) : les prets s'ecrivent directement dans leur table ({@code JpaLoanStore}), sans passer par
 * le modele global. Une ecriture n'a donc plus d'autre garantie de serialisation et d'atomicite que celles prises
 * ici : la sauvegarde et la suppression s'executent dans une transaction du {@link TransactionRunner}, apres la
 * prise du verrou du silo Credit ({@link SiloMutationLock}). La lecture de la liste courante, faite par le port
 * pour la reecrire, se fait ainsi sous ce verrou.
 *
 * <p>L'identifiant issu de l'URL n'est jamais {@code null} cote REST : un {@code null} est une erreur de
 * programmation, refusee avant toute ecriture ({@link IllegalArgumentException}). Un corps {@code null}
 * reste accepte pour {@link #saveLoanRow} (creation d'un pret par defaut, contrat historique de l'API).
 */
@Service
public class LoanCommandService {

    /** Silo ecrit par la sauvegarde et la suppression d'un pret. */
    private static final Set<MutationSilo> LOAN_SILOS = EnumSet.of(MutationSilo.CREDIT);

    private final LoanWriter loanWriter;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public LoanCommandService(LoanWriter loanWriter,
                              SiloMutationLock siloMutationLock,
                              TransactionRunner transactionRunner) {
        this.loanWriter = loanWriter;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        return transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(LOAN_SILOS);
            return loanWriter.saveLoanRow(body);
        });
    }

    public void deleteLoanRow(String id) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant du pret est obligatoire");
        }
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(LOAN_SILOS);
            loanWriter.deleteLoanRow(id);
            return null;
        });
    }
}
