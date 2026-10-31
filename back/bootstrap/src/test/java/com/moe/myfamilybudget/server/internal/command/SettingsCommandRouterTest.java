package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.EconomicAssumptionsCommandService;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.command.SettingsOwner;
import com.moe.myfamilybudget.application.command.SimulationSettingsCommandService;
import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;

/**
 * SET-020 : chaque paramètre de {@code /settings} est écrit par son owner, selon le tableau de
 * doc/architecture/12-settings.md. Sans Spring ni base de données.
 */
@DisplayName("SET-020 -- SettingsCommandRouter")
class SettingsCommandRouterTest {

    private ObjectifsSettingsService objectifs;
    private TaxCommandService tax;
    private RetirementCommandService retirement;
    private TresorerieCommandService tresorerie;
    private SimulationSettingsCommandService simulation;
    private EconomicAssumptionsCommandService economic;
    private SettingsCommandRouter router;

    @BeforeEach
    void setUp() {
        objectifs = mock(ObjectifsSettingsService.class);
        tax = mock(TaxCommandService.class);
        retirement = mock(RetirementCommandService.class);
        tresorerie = mock(TresorerieCommandService.class);
        simulation = mock(SimulationSettingsCommandService.class);
        economic = mock(EconomicAssumptionsCommandService.class);
        router = new SettingsCommandRouter(objectifs, tax, retirement, tresorerie, simulation, economic);
    }

    private void verifyNoOtherOwner(Object... untouched) {
        for (Object mock : untouched) {
            verifyNoInteractions(mock);
        }
    }

    @Test
    @DisplayName("Retraite : birthYear, retireAge, pass2026 et passGrowthRate vont à RetirementCommandService")
    void retirementFieldsGoToRetirement() {
        router.updateSetting("birthYear", 1980);
        router.updateSetting("retireAge", 62);
        router.updateSetting("pass2026", new BigDecimal("47100"));
        router.updateSetting("passGrowthRate", new BigDecimal("0.02"));

        verify(retirement).updateRetirementSetting(RetirementSettingField.BIRTH_YEAR, 1980);
        verify(retirement).updateRetirementSetting(RetirementSettingField.RETIRE_AGE, 62);
        verify(retirement).updateRetirementSetting(RetirementSettingField.PASS_2026, new BigDecimal("47100"));
        verify(retirement).updateRetirementSetting(RetirementSettingField.PASS_GROWTH_RATE, new BigDecimal("0.02"));
        verifyNoOtherOwner(tax, tresorerie, objectifs, simulation, economic);
    }

    @Test
    @DisplayName("Fiscalité : childExitAge et taxAbattement vont à TaxCommandService")
    void taxFieldsGoToTax() {
        router.updateSetting("childExitAge", 23);
        router.updateSetting("taxAbattement", new BigDecimal("0.10"));

        verify(tax).updateTaxSettings(TaxSettingField.CHILD_EXIT_AGE, 23);
        verify(tax).updateTaxSettings(TaxSettingField.TAX_ABATTEMENT, new BigDecimal("0.10"));
        verifyNoOtherOwner(retirement, tresorerie, objectifs, simulation, economic);
    }

    @Test
    @DisplayName("Trésorerie : pivot, solde de départ (et alias), sweep et plafonds vont à TresorerieCommandService")
    void tresorerieFieldsGoToTresorerie() {
        router.updateSetting("pivotDate", "2026-01-01");
        router.updateSetting("pivotMode", "manual");
        router.updateSetting("startBalance", new BigDecimal("1000"));
        router.updateSetting("pivotBalanceManual", new BigDecimal("2000"));
        router.updateSetting("sweepEnabled", true);
        router.updateSetting("cashCeiling", new BigDecimal("20000"));
        router.updateSetting("cashFloor", new BigDecimal("500"));
        router.updateSetting("cashAlertThreshold", new BigDecimal("300"));

        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.PIVOT_DATE, "2026-01-01");
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.PIVOT_MODE, "manual");
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.START_BALANCE, new BigDecimal("1000"));
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.PIVOT_BALANCE_MANUAL, new BigDecimal("2000"));
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.SWEEP_ENABLED, true);
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.CASH_CEILING, new BigDecimal("20000"));
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.CASH_FLOOR, new BigDecimal("500"));
        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.CASH_ALERT_THRESHOLD, new BigDecimal("300"));
        verifyNoOtherOwner(tax, retirement, objectifs, simulation, economic);
    }

    @Test
    @DisplayName("Objectifs : les seuils vont à ObjectifsSettingsService")
    void objectifsFieldsGoToObjectifs() {
        router.updateSetting("goalSecureHorizonMonths", 18);
        router.updateSetting("goalLiquidHorizonMonths", "6");

        verify(objectifs).updateField("goalSecureHorizonMonths", 18);
        verify(objectifs).updateField("goalLiquidHorizonMonths", "6");
        verifyNoOtherOwner(tax, retirement, tresorerie, simulation, economic);
    }

    @Test
    @DisplayName("Simulation : simulateUntilAge va à SimulationSettingsCommandService")
    void simulateUntilAgeGoesToSimulation() {
        router.updateSetting("simulateUntilAge", 90);

        verify(simulation).updateSimulateUntilAge(90);
        verifyNoOtherOwner(tax, retirement, tresorerie, objectifs, economic);
    }

    @Test
    @DisplayName("Hypothèses économiques : inflationRate va à EconomicAssumptionsCommandService")
    void inflationRateGoesToEconomicAssumptions() {
        router.updateSetting("inflationRate", new BigDecimal("0.025"));

        verify(economic).updateInflationRate(new BigDecimal("0.025"));
        verifyNoOtherOwner(tax, retirement, tresorerie, objectifs, simulation);
    }

    @Test
    @DisplayName("Un champ inconnu ou null est ignoré sans erreur et sans écriture")
    void unknownOrNullFieldIsIgnored() {
        router.updateSetting("unknownField", 1);
        router.updateSetting(null, 1);

        verifyNoOtherOwner(tax, retirement, tresorerie, objectifs, simulation, economic);
    }

    @Test
    @DisplayName("Une valeur null est transmise telle quelle à l'owner (contrat historique)")
    void nullValueIsForwarded() {
        router.updateSetting("cashCeiling", null);

        verify(tresorerie).updateTresorerieSetting(TresorerieSettingField.CASH_CEILING, null);
    }

    @Test
    @DisplayName("SET-030 : chaque clé de chaque enum de champs a exactement un owner, celui de son enum")
    void everySettingFieldKeyHasExactlyItsOwnOwner() {
        for (TaxSettingField field : TaxSettingField.values()) {
            assertThat(SettingsCommandRouter.ownerOf(field.key())).as("owner de %s", field.key())
                    .contains(SettingsOwner.FISCALITE);
        }
        for (RetirementSettingField field : RetirementSettingField.values()) {
            assertThat(SettingsCommandRouter.ownerOf(field.key())).as("owner de %s", field.key())
                    .contains(SettingsOwner.RETRAITE);
            assertThat(TaxSettingField.find(field.key())).as("%s n'est pas fiscal", field.key()).isEmpty();
        }
        for (TresorerieSettingField field : TresorerieSettingField.values()) {
            assertThat(SettingsCommandRouter.ownerOf(field.key())).as("owner de %s", field.key())
                    .contains(SettingsOwner.TRESORERIE);
            assertThat(TaxSettingField.find(field.key())).as("%s n'est pas fiscal", field.key()).isEmpty();
        }
    }

    @Test
    @DisplayName("SET-030 : seuls childExitAge et taxAbattement appartiennent à Fiscalité")
    void onlyChildExitAgeAndTaxAbattementBelongToTax() {
        long fiscal = Arrays.stream(TaxSettingField.values())
                .map(f -> SettingsCommandRouter.ownerOf(f.key()))
                .filter(owner -> owner.equals(Optional.of(SettingsOwner.FISCALITE)))
                .count();

        assertThat(fiscal).isEqualTo(2);
        assertThat(TaxSettingField.values()).hasSize(2);
        assertThat(SettingsCommandRouter.ownerOf("childExitAge")).contains(SettingsOwner.FISCALITE);
        assertThat(SettingsCommandRouter.ownerOf("taxAbattement")).contains(SettingsOwner.FISCALITE);
    }

    @Test
    @DisplayName("ownerOf : null et champ inconnu n'ont pas d'owner")
    void ownerOfUnknownIsEmpty() {
        assertThat(SettingsCommandRouter.ownerOf(null)).isEmpty();
        assertThat(SettingsCommandRouter.ownerOf("unknownField")).isEmpty();
    }
}
