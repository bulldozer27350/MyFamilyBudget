package com.moe.myfamilybudget.application.port;

import java.util.Set;

/**
 * Port du verrou de mutation par silo (SILO-206), qui remplace le verrou global du budget
 * ({@code BudgetMutationLock}) dans les façades de l'application.
 *
 * <p>Une façade transactionnelle déclare, en première instruction de sa transaction, l'ensemble des silos
 * qu'elle va écrire. Deux transactions qui visent des silos disjoints ne s'attendent plus ; deux transactions
 * qui partagent un silo sont sérialisées jusqu'à la fin de la première (validation ou annulation), pour que la
 * seconde parte toujours d'un état en base validé (VT-350b).
 *
 * <p>Contrat :
 * <ul>
 *   <li>tous les silos d'une transaction sont déclarés dans un seul appel (un second appel ne peut qu'ajouter
 *       des silos d'ordre supérieur à ceux déjà tenus, sinon {@link IllegalStateException}) ;</li>
 *   <li>les verrous sont pris dans l'ordre de {@link MutationSilo} et relâchés après la transaction ;</li>
 *   <li>sans transaction en cours, ou avec un ensemble vide, l'appel ne bloque rien.</li>
 * </ul>
 *
 * <p>L'implémentation est fournie par le composition root ({@code server}) ; ce port rejoindra
 * {@code application-api} (SILO-300).
 */
public interface SiloMutationLock {

    /** Verrouille {@code silos} pour toute la durée de la transaction en cours. */
    void lockForCurrentTransaction(Set<MutationSilo> silos);
}
