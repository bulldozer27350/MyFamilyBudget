package com.moe.myfamilybudget.server.internal.command;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;

/**
 * Service de commande du domaine Import Bancaire / Rapprochement (RF-A00, DB-040).
 * Unique point d'ecriture des transactions bancaires et rapprochements : valide la commande puis delegue
 * au port {@link BankWriter}. N'a plus de dependance directe vers {@code PersistenceManager}.
 *
 * <p>Un import {@code null} est une erreur de programmation, refusee avant toute ecriture
 * ({@link IllegalArgumentException}, traduite en 400 par le gestionnaire d'erreurs) : aucun appelant
 * actuel n'en transmet.
 */
@Service
public class BankImportCommandService {

    private final BankWriter bankWriter;

    public BankImportCommandService(BankWriter bankWriter) {
        this.bankWriter = bankWriter;
    }

    public void updateBankImport(BankImportModel bankImport) {
        if (bankImport == null) {
            throw new IllegalArgumentException("L'import bancaire est obligatoire");
        }
        bankWriter.updateBankImport(bankImport);
    }
}
