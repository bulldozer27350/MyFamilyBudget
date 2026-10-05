package com.moe.myfamilybudget.application.port;

import java.util.function.Supplier;

/**
 * Port d'ouverture d'une transaction par l'application (SILO-205, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>L'application orchestre plusieurs silos dans une même unité de travail : elle délimite la transaction
 * elle-même, sans annotation ni type Spring. L'implémentation est fournie par le composition root
 * ({@code server}, au moyen d'un {@code TransactionTemplate}) ; ce port rejoindra {@code application-api}
 * (SILO-300).
 *
 * <p>Sémantique : l'action s'exécute dans la transaction courante si elle existe, sinon dans une nouvelle
 * (propagation requise). Une exception non contrôlée ou une erreur annule la transaction et est propagée telle
 * quelle ; sinon la transaction est validée et le résultat renvoyé. Le verrou de mutation du budget doit rester
 * la première instruction de l'action.
 */
public interface TransactionRunner {

    /** Exécute {@code action} dans une transaction et renvoie son résultat (éventuellement {@code null}). */
    <T> T inTransaction(Supplier<T> action);
}
