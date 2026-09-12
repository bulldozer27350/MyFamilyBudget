package com.moe.myfamilybudget.server.internal.updater;

import java.util.Optional;

/**
 * Applique la mise à jour d'un champ nommé sur un enregistrement de type {@code T}.
 *
 * Remplace le pattern précédent ("field".equals(...) ? nouvelleValeur : ancienneValeur,
 * répété manuellement pour chaque champ et pour chaque type de ligne dans
 * PersistenceManager.updateTresorerieRow) par une association explicite nom-de-champ /
 * fonction de mise à jour, portée par une seule implémentation par modèle
 * (voir {@link MapBackedFieldUpdater} et les classes {@code *FieldUpdaters}).
 *
 * @param <T> le type d'enregistrement (IncomeModel, ChargeModel, PlacementModel, ...)
 */
@FunctionalInterface
public interface RecordFieldUpdater<T> {

    /**
     * @param record l'enregistrement existant
     * @param field  le nom du champ à modifier
     * @param value  la nouvelle valeur brute (telle que reçue de l'API, pas encore convertie)
     * @return le nouvel enregistrement avec le champ modifié, ou {@link Optional#empty()} si
     *         {@code field} n'est pas reconnu pour ce type d'enregistrement. Ce cas vide est le
     *         point clé : il permet à l'appelant de signaler explicitement une erreur au lieu de
     *         renvoyer silencieusement l'enregistrement inchangé.
     */
    Optional<T> update(T record, String field, Object value);
}
