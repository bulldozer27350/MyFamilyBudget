package com.moe.myfamilybudget.application.usecase.bankpointage;

/**
 * Signale qu'un identifiant de ligne (catégorie, règle, transaction...) passé au cas d'usage
 * {@link BankPointageUseCase} ne désigne aucune ligne connue (R-83, SILO-314). Le module {@code web}
 * (aujourd'hui les contrôleurs {@code StatementBankImportServiceImpl}) la traduit en 404.
 */
public class BankLigneNotFoundException extends RuntimeException {

    public BankLigneNotFoundException(String message) {
        super(message);
    }
}
