package com.moe.myfamilybudget.server.internal.port;

/**
 * Liste de lignes ciblee par une commande Tresorerie (DB-050). Remplace le {@code listKey} en chaine
 * dans les commands, les ports et les adaptateurs : la chaine de l'URL n'est interpretee qu'une seule
 * fois, a la frontiere REST, par {@link #fromKey(String)}.
 */
public enum TresorerieList {

    INCOMES("incomes"),
    CHARGES("charges"),
    ONEOFF("oneoff"),
    VARIABLE_INCOMES("variableIncomes"),
    VARIABLE_OVERRIDES("variableOverrides"),
    PLACEMENTS("placements");

    private final String key;

    TresorerieList(String key) {
        this.key = key;
    }

    /** Cle historique de la liste, utilisee uniquement par les adaptateurs de transition. */
    public String key() {
        return key;
    }

    /**
     * Interprete le segment d'URL (insensible a la casse).
     *
     * @throws IllegalArgumentException si {@code listKey} est {@code null} ou ne designe aucune liste
     *         (traduite en 400 par le gestionnaire d'erreurs)
     */
    public static TresorerieList fromKey(String listKey) {
        if (listKey == null) {
            throw new IllegalArgumentException("La liste de tresorerie (listKey) est obligatoire");
        }
        for (TresorerieList list : values()) {
            if (list.key.equalsIgnoreCase(listKey)) {
                return list;
            }
        }
        throw new IllegalArgumentException("Type de ligne de tresorerie inconnu : '" + listKey + "'");
    }
}
