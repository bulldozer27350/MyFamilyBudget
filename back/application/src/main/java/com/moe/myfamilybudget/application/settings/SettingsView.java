package com.moe.myfamilybudget.application.settings;

import java.math.BigDecimal;

/**
 * Paramètres généraux du budget, tels qu'exposés dans le bloc {@code settings} des réponses REST
 * composites ({@code /settings}, {@code /budget}, {@code /impots}, {@code /overview}, {@code /analyse},
 * {@code /pointage}, {@code /operations-en-cours}, {@code /retraite}).
 *
 * <p>R-52 : remplace {@code transition.model.SettingsModel} dans la couche application, pour que ce
 * bloc soit composé directement à partir des ports de lecture propriétaires (Retraite, Fiscalité,
 * Trésorerie, Simulation, Hypothèses économiques — voir {@link SettingsComposer}), sans passer par
 * {@code transition.port.SettingsReader} ni son assembleur. {@code transition.model.SettingsModel}
 * reste utilisé par la persistance et le hub global (hors périmètre de ce patch, voir R-61).
 */
public record SettingsView(
    Integer birthYear,
    Integer retireAge,
    Integer simulateUntilAge,
    BigDecimal inflationRate,
    String pivotDate,
    String pivotMode,
    BigDecimal startBalance,
    Integer childExitAge,
    BigDecimal taxAbattement,
    Boolean sweepEnabled,
    BigDecimal cashCeiling,
    BigDecimal cashFloor,
    BigDecimal cashAlertThreshold
) {
    public int getEffectiveBirthYear() {
        return birthYear != null ? birthYear : 1985;
    }

    public int getEffectiveRetireAge() {
        return retireAge != null ? retireAge : 64;
    }

    public int getEffectiveSimulateUntilAge() {
        return simulateUntilAge != null ? simulateUntilAge : 85;
    }

    public BigDecimal getEffectiveInflationRate() {
        return inflationRate != null ? inflationRate : BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveStartBalance() {
        return startBalance != null ? startBalance : BigDecimal.ZERO;
    }

    public int getEffectiveChildExitAge() {
        return childExitAge != null ? childExitAge : 21;
    }

    public BigDecimal getEffectiveTaxAbattement() {
        return taxAbattement != null ? taxAbattement : BigDecimal.ZERO;
    }
}
