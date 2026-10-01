package com.moe.myfamilybudget.server.internal.command;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.port.LoanWriter;

/**
 * Service de commande du domaine Prets/Emprunts (RF-A00, DB-041).
 * Unique point d'ecriture des prets : valide la commande puis delegue au port {@link LoanWriter}. Ne
 * depend plus de {@link PatrimoineCommandService} ni de {@code PersistenceManager}.
 *
 * <p>L'identifiant issu de l'URL n'est jamais {@code null} cote REST : un {@code null} est une erreur de
 * programmation, refusee avant toute ecriture ({@link IllegalArgumentException}). Un corps {@code null}
 * reste accepte pour {@link #saveLoanRow} (creation d'un pret par defaut, contrat historique de l'API).
 */
@Service
public class LoanCommandService {

    private final LoanWriter loanWriter;

    public LoanCommandService(LoanWriter loanWriter) {
        this.loanWriter = loanWriter;
    }

    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        return loanWriter.saveLoanRow(body);
    }

    public void deleteLoanRow(String id) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant du pret est obligatoire");
        }
        loanWriter.deleteLoanRow(id);
    }
}
