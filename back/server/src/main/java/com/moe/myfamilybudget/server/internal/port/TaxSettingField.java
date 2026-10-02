package com.moe.myfamilybudget.server.internal.port;

import java.util.Optional;

/**
 * Parametre de la famille Fiscalite ecrit via {@code PATCH /settings} (DB-050 lot 2, reduit par SET-030). Seuls
 * {@code childExitAge} et {@code taxAbattement} appartiennent a Fiscalite (voir 12-settings.md) : les autres
 * parametres ont leur propre enum chez leur owner ({@code RetirementSettingField},
 * {@code TresorerieSettingField}). La chaine recue par l'API n'est interpretee qu'une fois, a la frontiere
 * REST, par {@link #find(String)}, en respectant la casse.
 */
public enum TaxSettingField {

    CHILD_EXIT_AGE("childExitAge"),
    TAX_ABATTEMENT("taxAbattement");

    private final String key;

    TaxSettingField(String key) {
        this.key = key;
    }

    /** Nom historique du champ, utilise uniquement par les adaptateurs de transition. */
    public String key() {
        return key;
    }

    /**
     * Interprete un nom de champ recu par l'API. Un champ inconnu (ou {@code null}) ne designe aucun
     * parametre fiscal : le comportement historique (aucune modification) est conserve par l'appelant.
     */
    public static Optional<TaxSettingField> find(String field) {
        if (field == null) {
            return Optional.empty();
        }
        for (TaxSettingField candidate : values()) {
            if (candidate.key.equals(field)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
