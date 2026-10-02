package com.moe.myfamilybudget.server.internal.port;

/**
 * Liste de lignes ciblee par une commande Patrimoine (DB-050). Les prets et les objectifs ne figurent pas
 * ici : ils ont leurs propres command services et ports ({@code LoanWriter}, {@code GoalWriter}).
 */
public enum PatrimoineList {

    PLACEMENTS("placements"),
    TRANSFERS("transfers"),
    REAL_ESTATE("realEstate");

    private final String key;

    PatrimoineList(String key) {
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
    public static PatrimoineList fromKey(String listKey) {
        if (listKey == null) {
            throw new IllegalArgumentException("La liste patrimoine (listKey) est obligatoire");
        }
        for (PatrimoineList list : values()) {
            if (list.key.equalsIgnoreCase(listKey)) {
                return list;
            }
        }
        throw new IllegalArgumentException("Type de ligne de patrimoine inconnu : '" + listKey + "'");
    }
}
