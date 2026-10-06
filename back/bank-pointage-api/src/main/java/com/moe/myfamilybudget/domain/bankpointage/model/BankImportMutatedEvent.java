package com.moe.myfamilybudget.domain.bankpointage.model;

/**
 * Evenement publie par l'adaptateur du silo Banque/Pointage apres chaque ecriture de l'import bancaire
 * (SILO-213, lot B).
 *
 * <p>Depuis que l'import bancaire s'ecrit directement dans sa table, il ne passe plus par le
 * {@code PersistenceManager} et son {@code BudgetMutatedEvent} : cet evenement conserve la regle « toute
 * modification declenche un controle des notifications ». Il est ecoute apres le commit de la transaction en
 * cours, dans le composition root.
 *
 * @param mutationKind nature de l'ecriture (diagnostic uniquement : {@code updateBankImport}, {@code replace},
 *                     {@code reset})
 */
public record BankImportMutatedEvent(String mutationKind) {
}
