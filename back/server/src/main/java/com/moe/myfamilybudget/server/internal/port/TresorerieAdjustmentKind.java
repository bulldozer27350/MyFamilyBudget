package com.moe.myfamilybudget.server.internal.port;

/**
 * Nature de la ligne visee par un ajustement de tresorerie (DB-050). Remplace le {@code kind} en chaine
 * ({@code "charge"}, {@code "revenu"}, {@code "placement"}) dans les commands, ports et adaptateurs.
 */
public enum TresorerieAdjustmentKind {

    CHARGE("charge"),
    INCOME("revenu"),
    PLACEMENT("placement");

    private final String kind;

    TresorerieAdjustmentKind(String kind) {
        this.kind = kind;
    }

    /** Valeur historique du type de ligne, utilisee uniquement par les adaptateurs de transition. */
    public String kind() {
        return kind;
    }

    /**
     * Interprete le type de ligne recu par l'API. Comportement historique conserve : {@code "charge"}
     * vise les charges, {@code "revenu"} / {@code "income"} les revenus, toute autre valeur les placements.
     *
     * @throws IllegalArgumentException si {@code kind} est {@code null}
     */
    public static TresorerieAdjustmentKind fromKind(String kind) {
        if (kind == null) {
            throw new IllegalArgumentException("Le type de ligne a ajuster est obligatoire");
        }
        if ("charge".equalsIgnoreCase(kind)) {
            return CHARGE;
        }
        if ("revenu".equalsIgnoreCase(kind) || "income".equalsIgnoreCase(kind)) {
            return INCOME;
        }
        return PLACEMENT;
    }
}
