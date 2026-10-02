package com.moe.myfamilybudget.server.internal.port;

import java.util.Optional;

/**
 * Champ modifiable d'une categorie d'actif (DB-050, lot 2). Remplace le nom de champ en chaine dans la
 * command, le port et l'adaptateur Patrimoine ; interprete une seule fois a la frontiere REST par
 * {@link #find(String)}. Les cles sont comparees en respectant la casse.
 */
public enum AssetCategoryField {

    ICON("icon"),
    NAME("name"),
    BUCKET("bucket"),
    COLOR("color");

    private final String key;

    AssetCategoryField(String key) {
        this.key = key;
    }

    /** Nom historique du champ, utilise uniquement par les adaptateurs de transition. */
    public String key() {
        return key;
    }

    /** Un champ inconnu (ou {@code null}) ne designe aucun champ : l'appelant ne modifie rien. */
    public static Optional<AssetCategoryField> find(String field) {
        if (field == null) {
            return Optional.empty();
        }
        for (AssetCategoryField candidate : values()) {
            if (candidate.key.equals(field)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
