package com.moe.myfamilybudget.server.internal.port;

import java.util.Optional;

/**
 * Cellule modifiable d'une ligne de tresorerie (DB-050, lot 3). Remplace le nom de champ en chaine dans
 * la command, le port et l'adaptateur Tresorerie ; interprete une seule fois a la frontiere REST par
 * {@link #find(String)}. Les cles sont comparees en respectant la casse.
 *
 * <p>Cet enum est l'union des champs de toutes les listes ({@link TresorerieList}). La validite d'un
 * champ pour une liste donnee reste verifiee par les {@code *FieldUpdaters} : un champ connu mais sans
 * objet pour la liste visee (ex. {@code ratePess} sur {@code charges}) est refuse comme auparavant
 * ({@code UnknownTresorerieFieldException}, 400).
 */
public enum TresorerieLineField {

    LABEL("label"),
    NOTES("notes"),
    MONTHLY("monthly"),
    START("start"),
    END("end"),
    GROWTH_RATE("growthRate"),
    CATEGORY_ID("categoryId"),
    DATE("date"),
    AMOUNT("amount"),
    REF_INCOME_LABEL("refIncomeLabel"),
    RATE("rate"),
    START_YEAR("startYear"),
    END_YEAR("endYear"),
    TAXABLE("taxable"),
    TYPE("type"),
    YEAR("year"),
    CATEGORY("category"),
    BALANCE("balance"),
    BALANCE_DATE("balanceDate"),
    MONTHLY_FROM("monthlyFrom"),
    MONTHLY_UNTIL("monthlyUntil"),
    RATE_PESS("ratePess"),
    RATE_CORR("rateCorr"),
    RATE_OPTI("rateOpti"),
    EXCLUDED_FROM_RETIREMENT("excludedFromRetirement"),
    SWEEP_PRIORITY("sweepPriority"),
    SWEEP_CAP("sweepCap"),
    PAUSE_TRIGGER_BALANCE("pauseTriggerBalance"),
    PAUSE_PRIORITY("pausePriority");

    private final String key;

    TresorerieLineField(String key) {
        this.key = key;
    }

    /** Nom historique du champ, utilise uniquement par les adaptateurs de transition. */
    public String key() {
        return key;
    }

    /** Un champ inconnu (ou {@code null}) ne designe aucune cellule : l'appelant le refuse (400). */
    public static Optional<TresorerieLineField> find(String field) {
        if (field == null) {
            return Optional.empty();
        }
        for (TresorerieLineField candidate : values()) {
            if (candidate.key.equals(field)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
