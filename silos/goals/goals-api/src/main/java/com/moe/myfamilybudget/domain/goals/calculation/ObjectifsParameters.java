package com.moe.myfamilybudget.domain.goals.calculation;

/**
 * Paramètres propres au domaine Objectifs (RF-700, voir doc/architecture/12-settings.md) : seuils
 * de bascule liquide/illiquide d'un objectif d'épargne. Auparavant portés par {@code SettingsModel}.
 *
 * <p>Une valeur {@code null} signifie « non renseignée » : {@link #getEffectiveSecureHorizonMonths()}
 * et {@link #getEffectiveLiquidHorizonMonths()} appliquent alors la valeur par défaut. Les valeurs
 * brutes sont conservées telles quelles pour l'API, qui les expose inchangées.
 *
 * @param secureHorizonMonths horizon (en mois) en deçà duquel un objectif est « à sécuriser »
 * @param liquidHorizonMonths horizon (en mois) en deçà duquel un objectif est « à rapatrier »
 */
public record ObjectifsParameters(Integer secureHorizonMonths, Integer liquidHorizonMonths) {

    public static final int DEFAULT_SECURE_HORIZON_MONTHS = 12;
    public static final int DEFAULT_LIQUID_HORIZON_MONTHS = 3;

    /** Aucun seuil renseigné : les valeurs par défaut s'appliquent via les getters « effective ». */
    public static ObjectifsParameters defaults() {
        return new ObjectifsParameters(null, null);
    }

    public int getEffectiveSecureHorizonMonths() {
        return secureHorizonMonths != null ? secureHorizonMonths : DEFAULT_SECURE_HORIZON_MONTHS;
    }

    public int getEffectiveLiquidHorizonMonths() {
        return liquidHorizonMonths != null ? liquidHorizonMonths : DEFAULT_LIQUID_HORIZON_MONTHS;
    }

    public ObjectifsParameters withSecureHorizonMonths(Integer value) {
        return new ObjectifsParameters(value, liquidHorizonMonths);
    }

    public ObjectifsParameters withLiquidHorizonMonths(Integer value) {
        return new ObjectifsParameters(secureHorizonMonths, value);
    }
}
