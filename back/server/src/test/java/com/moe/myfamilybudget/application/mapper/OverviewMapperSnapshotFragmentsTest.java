package com.moe.myfamilybudget.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.SettingsDto;
import com.moe.myfamilybudget.api.model.TransferDto;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;

/**
 * SILO-119 (lot B2) : décomposition d'un {@code BudgetDataDto} importé en fragments par silo, sans Spring
 * ni base de données.
 */
class OverviewMapperSnapshotFragmentsTest {

    private final OverviewMapper mapper = new OverviewMapper();

    private static SettingsDto settingsDto() {
        SettingsDto s = new SettingsDto();
        s.setBirthYear(1990);
        s.setRetireAge(62);
        s.setSimulateUntilAge(90);
        s.setInflationRate(new BigDecimal("0.03"));
        s.setPivotDate("2027-01-01");
        s.setPivotMode("auto");
        s.setStartBalance(new BigDecimal("1500"));
        s.setChildExitAge(25);
        s.setTaxAbattement(new BigDecimal("0.12"));
        s.setSweepEnabled(Boolean.TRUE);
        s.setCashCeiling(new BigDecimal("20000"));
        s.setCashFloor(new BigDecimal("1000"));
        s.setCashAlertThreshold(new BigDecimal("300"));
        s.setGoalSecureHorizonMonths(6);
        s.setGoalLiquidHorizonMonths(2);
        return s;
    }

    @Test
    @DisplayName("settings est réparti chez les propriétaires (Retraite, Fiscalité, Trésorerie, Simulation, Hypothèses, Objectifs)")
    void settingsAreSplitByOwner() {
        BudgetDataDto dto = new BudgetDataDto();
        dto.setSettings(settingsDto());

        BudgetSnapshotFragments f = mapper.toSnapshotFragments(dto);

        assertThat(f.retirementSettings()).isEqualTo(new RetirementSettingsModel(1990, 62));
        assertThat(f.taxSettings()).isEqualTo(new TaxSettingsModel(25, new BigDecimal("0.12")));
        assertThat(f.tresorerieSettings()).isEqualTo(new TresorerieSettingsModel("2027-01-01", "auto",
                new BigDecimal("1500"), Boolean.TRUE, new BigDecimal("20000"), new BigDecimal("1000"),
                new BigDecimal("300")));
        assertThat(f.simulationSettings()).isEqualTo(new SimulationSettingsModel(90));
        assertThat(f.economicAssumptions()).isEqualTo(new EconomicAssumptionsModel(new BigDecimal("0.03")));
        assertThat(f.objectifsParameters()).isEqualTo(new ObjectifsParameters(6, 2));
    }

    @Test
    @DisplayName("settings absent : paramètres null (valeur par défaut côté ports), listes vides")
    void absentSettingsAndListsAreNullAndEmpty() {
        BudgetSnapshotFragments f = mapper.toSnapshotFragments(new BudgetDataDto());

        assertThat(f.retirementSettings()).isNull();
        assertThat(f.taxSettings()).isNull();
        assertThat(f.tresorerieSettings()).isNull();
        assertThat(f.simulationSettings()).isNull();
        assertThat(f.economicAssumptions()).isNull();
        assertThat(f.objectifsParameters()).isEqualTo(ObjectifsParameters.defaults());
        assertThat(f.incomes()).isEmpty();
        assertThat(f.charges()).isEmpty();
        assertThat(f.oneoff()).isEmpty();
        assertThat(f.variableIncomes()).isEmpty();
        assertThat(f.variableOverrides()).isEmpty();
        assertThat(f.taxChildren()).isEmpty();
        assertThat(f.taxBrackets()).isEmpty();
        assertThat(f.taxRateOverrides()).isEmpty();
        assertThat(f.taxActualOverrides()).isEmpty();
        assertThat(f.placements()).isEmpty();
        assertThat(f.realEstate()).isEmpty();
        assertThat(f.transfers()).isEmpty();
        assertThat(f.assetCategories()).isEmpty();
        assertThat(f.loans()).isEmpty();
        assertThat(f.goals()).isEmpty();
    }

    @Test
    @DisplayName("les virements sont lus avec le type du silo Patrimoine")
    void transfersUseTheWealthType() {
        TransferDto t = new TransferDto();
        t.setId("tr_1");
        t.setPlacement("PEA");
        t.setDate("2026-03-01");
        t.setAmount(new BigDecimal("500"));
        t.setNotes("n");
        BudgetDataDto dto = new BudgetDataDto();
        dto.setTransfers(List.of(t));

        BudgetSnapshotFragments f = mapper.toSnapshotFragments(dto);

        assertThat(f.transfers()).containsExactly(
                new PatrimoineTransferModel("tr_1", "PEA", "2026-03-01", new BigDecimal("500"), "n"));
    }

    @Test
    @DisplayName("corps null : fragments vides et sans paramètres")
    void nullBodyGivesEmptyFragments() {
        BudgetSnapshotFragments f = mapper.toSnapshotFragments(null);

        assertThat(f.retirementSettings()).isNull();
        assertThat(f.retirement()).isNull();
        assertThat(f.bankImport()).isNull();
        assertThat(f.incomes()).isEmpty();
        assertThat(f.objectifsParameters()).isEqualTo(ObjectifsParameters.defaults());
    }
}
