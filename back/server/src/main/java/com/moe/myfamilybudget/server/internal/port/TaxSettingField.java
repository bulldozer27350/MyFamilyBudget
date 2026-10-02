package com.moe.myfamilybudget.server.internal.port;

import java.util.Optional;

/**
 * Parametre ecrit par la commande {@code updateTaxSettings} (DB-050, lot 2). Remplace le nom de champ en
 * chaine dans la command, le port et l'adaptateur Fiscalite : la chaine recue par l'API n'est interpretee
 * qu'une fois, a la frontiere REST, par {@link #find(String)}.
 *
 * <p>Les cles sont celles de l'ancien contrat ({@code field} / {@code value}), comparees en respectant la
 * casse. {@code startBalance} et {@code pivotBalanceManual} ecrivent le meme parametre.
 */
public enum TaxSettingField {

    BIRTH_YEAR("birthYear"),
    RETIRE_AGE("retireAge"),
    SIMULATE_UNTIL_AGE("simulateUntilAge"),
    INFLATION_RATE("inflationRate"),
    PIVOT_DATE("pivotDate"),
    PIVOT_MODE("pivotMode"),
    START_BALANCE("startBalance"),
    PIVOT_BALANCE_MANUAL("pivotBalanceManual"),
    CHILD_EXIT_AGE("childExitAge"),
    TAX_ABATTEMENT("taxAbattement"),
    PASS_2026("pass2026"),
    PASS_GROWTH_RATE("passGrowthRate"),
    SWEEP_ENABLED("sweepEnabled"),
    CASH_CEILING("cashCeiling"),
    CASH_FLOOR("cashFloor"),
    CASH_ALERT_THRESHOLD("cashAlertThreshold");

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
