package com.moe.myfamilybudget.server.internal.port;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Port d'ecriture pour le domaine Tresorerie (DB-031). Seul le command service du domaine
 * ({@code TresorerieCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et
 * prepare la bascule JPA (DB-1061) sans changer l'appelant.
 *
 * <p>Il n'existe pas d'entite Tresorerie dediee : les lignes ecrites sont celles du budget de base
 * (revenus, charges, depenses ponctuelles, revenus variables, surcharges variables) et des placements.
 * Les contrats {@code listKey} / {@code field} / {@code value} sont conserves tels quels : leur retrait
 * releve de DB-050.
 */
public interface TresorerieWriter {

    /** Ajoute une ligne a la liste {@code listKey} ; renvoie la ligne creee (dont son {@code id}). */
    Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body);

    /** Met a jour une cellule ({@code field}) d'une ligne ; {@code value} peut etre {@code null}. */
    void updateTresorerieRow(String listKey, String id, String field, Object value);

    void removeTresorerieRow(String listKey, String id);

    /** Applique un nouveau montant mensuel a une ligne de charges, revenus ou placements. */
    void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly);
}
