package com.moe.myfamilybudget.domain.treasury.port;

import java.util.Optional;

/**
 * Paramètre de la famille Trésorerie écrit via {@code PATCH /settings} (SET-020, voir
 * doc/architecture/12-settings.md). Les clés sont celles du contrat actuel, comparées en respectant la
 * casse. {@code startBalance} et {@code pivotBalanceManual} écrivent le même paramètre.
 */
public enum TresorerieSettingField {

    PIVOT_DATE("pivotDate"),
    PIVOT_MODE("pivotMode"),
    START_BALANCE("startBalance"),
    PIVOT_BALANCE_MANUAL("pivotBalanceManual"),
    SWEEP_ENABLED("sweepEnabled"),
    CASH_CEILING("cashCeiling"),
    CASH_FLOOR("cashFloor"),
    CASH_ALERT_THRESHOLD("cashAlertThreshold");

    private final String key;

    TresorerieSettingField(String key) {
        this.key = key;
    }

    /** Nom historique du champ, utilisé uniquement par les adaptateurs de transition. */
    public String key() {
        return key;
    }

    /** Un champ inconnu (ou {@code null}) ne désigne aucun paramètre Trésorerie. */
    public static Optional<TresorerieSettingField> find(String field) {
        if (field == null) {
            return Optional.empty();
        }
        for (TresorerieSettingField candidate : values()) {
            if (candidate.key.equals(field)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
