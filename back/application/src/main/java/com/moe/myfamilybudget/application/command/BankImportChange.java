package com.moe.myfamilybudget.application.command;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;

/**
 * Resultat d'une modification de l'import bancaire calculee sous verrou par
 * {@link BankImportCommandService#modifyBankImport} (SILO-213, lot B) : l'import a ecrire, ou {@code null}
 * pour ne rien ecrire (cas refuse, introuvable ou sans effet), et la valeur que l'appelant veut recuperer
 * (reponse, statistiques d'import...).
 *
 * @param updatedImport import a ecrire, {@code null} : aucune ecriture
 * @param result        valeur renvoyee a l'appelant (peut etre {@code null})
 * @param <T>           type de la valeur renvoyee
 */
public record BankImportChange<T>(BankImportModel updatedImport, T result) {

    /** L'import {@code updated} est ecrit, {@code result} est renvoye. */
    public static <T> BankImportChange<T> write(BankImportModel updated, T result) {
        if (updated == null) {
            throw new IllegalArgumentException("L'import bancaire est obligatoire");
        }
        return new BankImportChange<>(updated, result);
    }

    /** Aucune ecriture, {@code result} est renvoye. */
    public static <T> BankImportChange<T> unchanged(T result) {
        return new BankImportChange<>(null, result);
    }
}
