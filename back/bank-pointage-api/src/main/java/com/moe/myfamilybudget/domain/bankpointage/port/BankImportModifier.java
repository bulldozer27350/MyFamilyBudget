package com.moe.myfamilybudget.domain.bankpointage.port;

import java.util.function.Function;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;

/**
 * Port de modification de l'import bancaire sans perte de modification concurrente (SILO-213, lot B).
 *
 * <p>Lire l'import, le modifier puis le reecrire en entier en trois temps independants perd la modification
 * d'une ecriture concurrente. Ce port fait les trois dans une unite de travail unique : l'implementation lit
 * l'import <em>apres</em> avoir pris le verrou d'ecriture du silo Banque/Pointage, applique la modification
 * puis ecrit l'import renvoye (aucune ecriture si {@link BankImportChange#updatedImport()} est {@code null}).
 * Le composition root fournit l'implementation ({@code BankImportCommandService}) : le silo n'a ainsi aucune
 * dependance vers l'application, ni vers un type transactionnel.
 *
 * <p>La modification s'execute sous verrou : elle reste un calcul sur l'import recu, sans appel long ni
 * lecture d'un autre silo. Une exception de la modification annule l'unite de travail et est propagee telle
 * quelle ; rien n'est ecrit.
 */
public interface BankImportModifier {

    /**
     * Lit l'import courant sous verrou, applique {@code modification}, ecrit le resultat.
     *
     * @return la valeur {@link BankImportChange#result()} de la modification
     */
    <T> T modifyBankImport(Function<BankImportModel, BankImportChange<T>> modification);
}
