package com.moe.myfamilybudget.domain.treasury.port;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Port d'ecriture pour le domaine Tresorerie (DB-031). Seul le command service du domaine
 * ({@code TresorerieCommandService}) l'utilise : il isole le domaine du {@code PersistenceManager} et
 * prepare la bascule JPA (DB-1061) sans changer l'appelant.
 *
 * <p>Il n'existe pas d'entite Tresorerie dediee : les lignes ecrites sont celles du budget de base
 * (revenus, charges, depenses ponctuelles, revenus variables, surcharges variables) et des placements.
 * DB-050 : la liste ({@link TresorerieList}) et la nature d'ajustement ({@link TresorerieAdjustmentKind})
 * et la cellule ({@link TresorerieLineField}) sont typees ; seule la valeur reste brute, convertie par les
 * {@code *FieldUpdaters} selon le champ.
 */
public interface TresorerieWriter {

    /** Ajoute une ligne a la liste {@code list} ; renvoie la ligne creee (dont son {@code id}). */
    Map<String, Object> addTresorerieRow(TresorerieList list, Map<String, Object> body);

    /** Met a jour une cellule ({@code field}) d'une ligne ; {@code value} peut etre {@code null}. */
    void updateTresorerieRow(TresorerieList list, String id, TresorerieLineField field, Object value);

    void removeTresorerieRow(TresorerieList list, String id);

    /** Applique un nouveau montant mensuel a une ligne de charges, revenus ou placements. */
    void applyTresorerieAjustement(String lineId, TresorerieAdjustmentKind kind, BigDecimal newMonthly);

    /**
     * SET-020 : met à jour un paramètre de la famille Trésorerie exposé par {@code PATCH /settings}
     * (pivot, solde de départ, sweep, plafonds de cash).
     */
    void updateTresorerieSetting(TresorerieSettingField field, Object value);
}
