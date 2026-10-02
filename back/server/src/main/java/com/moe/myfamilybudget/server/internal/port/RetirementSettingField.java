package com.moe.myfamilybudget.server.internal.port;

import java.util.Optional;

/**
 * Paramètre de la famille Retraite écrit via {@code PATCH /settings} (SET-020, voir
 * doc/architecture/12-settings.md). Les clés sont celles du contrat actuel, comparées en respectant la
 * casse ; la chaîne reçue par l'API n'est interprétée qu'une fois, à la frontière REST, par
 * {@link #find(String)}.
 */
public enum RetirementSettingField {

    BIRTH_YEAR("birthYear"),
    RETIRE_AGE("retireAge"),
    PASS_2026("pass2026"),
    PASS_GROWTH_RATE("passGrowthRate");

    private final String key;

    RetirementSettingField(String key) {
        this.key = key;
    }

    /** Nom historique du champ, utilisé uniquement par les adaptateurs de transition. */
    public String key() {
        return key;
    }

    /** Un champ inconnu (ou {@code null}) ne désigne aucun paramètre Retraite. */
    public static Optional<RetirementSettingField> find(String field) {
        if (field == null) {
            return Optional.empty();
        }
        for (RetirementSettingField candidate : values()) {
            if (candidate.key.equals(field)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
