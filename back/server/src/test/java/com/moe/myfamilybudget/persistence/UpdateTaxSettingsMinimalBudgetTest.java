package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;
import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsRepository;
import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

/**
 * FIX-020 -- Modifier un parametre ne depend plus de la presence de {@code sweepEnabled}.
 *
 * <p>SET-030 : le dispatcher generique {@code updateTaxSettings(String, Object)} est remplace par une mutation
 * explicite par owner ; ces tests couvrent les cinq. Avant FIX-020, la mutation melangeait un {@code boolean} primitif et le
 * {@code Boolean} nullable {@code sweepEnabled} dans une expression ternaire : l'unboxing implicite levait une
 * {@link NullPointerException} des qu'un autre champ etait modifie sur un budget dont {@code sweepEnabled}
 * n'etait pas renseigne. Ces tests partent d'un budget minimal, sans ce parametre.
 */
class UpdateTaxSettingsMinimalBudgetTest {

    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        BudgetDataRepository budgetDataRepository = mock(BudgetDataRepository.class);
        doAnswer(invocation -> invocation.getArgument(0)).when(budgetDataRepository).save(any());

        persistenceManager = new PersistenceManager(
                budgetDataRepository,
                mock(SettingsRepository.class),
                mock(PlacementRepository.class),
                mock(RealEstateRepository.class),
                mock(AssetCategoryRepository.class),
                mock(GoalRepository.class),
                mock(FiscalChildRepository.class),
                mock(FiscalBracketRepository.class),
                mock(FiscalRateOverrideRepository.class),
                mock(FiscalActualOverrideRepository.class),
                mock(PensionPlanRepository.class),
                mock(BankImportDocumentRepository.class),
                mock(WealthPlacementRepository.class),
                mock(WealthRealEstateRepository.class),
                mock(WealthCategoryRepository.class),
                mock(PensionSettingsRepository.class),
                mock(FiscalSettingsRepository.class),
                mock(CashflowSettingsRepository.class),
                mock(AppSettingsRepository.class),
                mock(PlatformTransactionManager.class),
                mock(ApplicationEventPublisher.class));
        persistenceManager.init();

        // Budget minimal : tous les parametres de tresorerie facultatifs (sweep, cash) sont absents.
        persistenceManager.setBudgetData(persistenceManager.getBudgetData().withSettings(new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "auto", BigDecimal.ZERO, 21,
                new BigDecimal("0.10"), null, null, null, null)));
        assertThat(settings().sweepEnabled()).isNull();
    }

    @Test
    @DisplayName("Modifier un parametre sur un budget sans sweepEnabled ne leve pas d'exception")
    void updatingAnyFieldDoesNotRequireSweepEnabled() {
        assertThatCode(() -> {
            update("retireAge", 60);
            update("birthYear", 1980);
            update("simulateUntilAge", 90);
            update("inflationRate", "0.03");
            update("pivotDate", "2027-01-01");
            update("pivotMode", "manual");
            update("startBalance", 1500);
            update("childExitAge", 25);
            update("taxAbattement", "0.12");
            update("pass2026", 48000);
            update("passGrowthRate", "0.02");
            update("cashCeiling", 9000);
            update("cashFloor", 500);
            update("cashAlertThreshold", 200);
        }).doesNotThrowAnyException();

        SettingsModel s = settings();
        assertThat(s.retireAge()).isEqualTo(60);
        assertThat(s.birthYear()).isEqualTo(1980);
        assertThat(s.childExitAge()).isEqualTo(25);
        assertThat(s.taxAbattement()).isEqualByComparingTo("0.12");
        assertThat(s.cashCeiling()).isEqualByComparingTo("9000");
        assertThat(s.cashFloor()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Un champ sans rapport avec sweepEnabled le laisse tel quel (absent reste absent)")
    void untouchedSweepEnabledIsLeftUnchanged() {
        update("retireAge", 62);

        assertThat(settings().retireAge()).isEqualTo(62);
        assertThat(settings().sweepEnabled()).isNull();
    }

    @Test
    @DisplayName("sweepEnabled reste modifiable : booleen, chaine ou valeur absente (= desactive)")
    void sweepEnabledCanStillBeSet() {
        update("sweepEnabled", true);
        assertThat(settings().sweepEnabled()).isTrue();

        update("sweepEnabled", "false");
        assertThat(settings().sweepEnabled()).isFalse();

        update("sweepEnabled", "true");
        assertThat(settings().sweepEnabled()).isTrue();

        update("sweepEnabled", null);
        assertThat(settings().sweepEnabled()).isFalse();
    }

    @Test
    @DisplayName("SET-040 : pass2026 et passGrowthRate sont ecrits dans la retraite, pas dans les parametres")
    void passParametersAreWrittenToRetirementOnly() {
        SettingsModel settingsBefore = settings();

        update("pass2026", 48000);
        update("passGrowthRate", "0.02");

        RetirementModel retirement = persistenceManager.getBudgetData().retirement();
        assertThat(retirement.pass2026()).isEqualByComparingTo("48000");
        assertThat(retirement.passGrowthRate()).isEqualByComparingTo("0.02");
        assertThat(settings()).isEqualTo(settingsBefore);
    }

    @Test
    @DisplayName("SET-030 : une mutation d'owner ne modifie que les champs de cet owner")
    void ownerMutationsOnlyTouchTheirOwnFields() {
        SettingsModel before = settings();

        update("retireAge", 60);
        assertThat(settings()).isEqualTo(new SettingsModel(before.birthYear(), 60, before.simulateUntilAge(),
                before.inflationRate(), before.pivotDate(), before.pivotMode(), before.startBalance(),
                before.childExitAge(), before.taxAbattement(), null, null, null, null));

        update("childExitAge", 25);
        assertThat(settings().childExitAge()).isEqualTo(25);
        assertThat(settings().retireAge()).isEqualTo(60);

        update("pivotMode", "manual");
        assertThat(settings().pivotMode()).isEqualTo("manual");
        assertThat(settings().childExitAge()).isEqualTo(25);

        update("simulateUntilAge", 90);
        assertThat(settings().simulateUntilAge()).isEqualTo(90);
        assertThat(settings().pivotMode()).isEqualTo("manual");
    }

    @Test
    @DisplayName("SET-030 : startBalance et pivotBalanceManual ecrivent le meme parametre Tresorerie")
    void startBalanceAndPivotBalanceManualShareTheSameSetting() {
        update("startBalance", 1500);
        assertThat(settings().startBalance()).isEqualByComparingTo("1500");

        update("pivotBalanceManual", 2500);
        assertThat(settings().startBalance()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("SET-030 : chaque champ de chaque enum d'owner est effectivement applique (garde-fou switch)")
    void everyOwnerSettingFieldIsApplied() {
        Map<String, Object> samples = Map.ofEntries(
                Map.entry("birthYear", 1970), Map.entry("retireAge", 61),
                Map.entry("pass2026", 50000), Map.entry("passGrowthRate", "0.03"),
                Map.entry("pivotDate", "2030-01-01"), Map.entry("pivotMode", "manual"),
                Map.entry("startBalance", 777), Map.entry("pivotBalanceManual", 888),
                Map.entry("sweepEnabled", true), Map.entry("cashCeiling", 1234),
                Map.entry("cashFloor", 12), Map.entry("cashAlertThreshold", 34),
                Map.entry("childExitAge", 30), Map.entry("taxAbattement", "0.20"));

        for (RetirementSettingField field : RetirementSettingField.values()) {
            assertApplied(field.key(), samples);
        }
        for (TresorerieSettingField field : TresorerieSettingField.values()) {
            assertApplied(field.key(), samples);
        }
        for (TaxSettingField field : TaxSettingField.values()) {
            assertApplied(field.key(), samples);
        }
    }

    private void assertApplied(String key, Map<String, Object> samples) {
        assertThat(samples).as("valeur d'exemple pour %s", key).containsKey(key);
        BudgetDataModel before = persistenceManager.getBudgetData();

        update(key, samples.get(key));

        BudgetDataModel after = persistenceManager.getBudgetData();
        // SET-040 : pass2026 / passGrowthRate modifient la retraite, pas les parametres.
        boolean changed = !Objects.equals(before.settings(), after.settings())
                || !Objects.equals(before.retirement(), after.retirement());
        assertThat(changed).as("%s doit modifier les parametres ou la retraite", key).isTrue();
    }

    /** Route le champ vers la mutation de son owner, comme le fait {@code SettingsCommandRouter}. */
    private void update(String field, Object value) {
        RetirementSettingField.find(field).ifPresent(
                f -> persistenceManager.write(m -> m.updateRetirementSetting(f, value)));
        TresorerieSettingField.find(field).ifPresent(
                f -> persistenceManager.write(m -> m.updateTresorerieSetting(f, value)));
        TaxSettingField.find(field).ifPresent(
                f -> persistenceManager.write(m -> m.updateFiscalSetting(f, value)));
        if ("simulateUntilAge".equals(field)) {
            persistenceManager.write(m -> m.updateSimulateUntilAge(value));
        }
        if ("inflationRate".equals(field)) {
            persistenceManager.write(m -> m.updateInflationRate(value));
        }
    }

    private SettingsModel settings() {
        return persistenceManager.getBudgetData().settings();
    }
}
