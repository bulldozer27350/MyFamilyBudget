package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.domain.analysis.model.AnalyseResultModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

/**
 * RES-010 : les mappers Overview/Analyse consomment la vue de façade {@link BudgetFacadeView}
 * (et non {@code BudgetDataModel}) pour les champs de budget réexposés par l'API, sans Spring ni
 * base de données.
 */
class BudgetFacadeViewMapperTest {

    private final OverviewMapper overviewMapper = new OverviewMapper();
    private final AnalyseMapper analyseMapper = new AnalyseMapper(overviewMapper);

    private static SettingsModel settings() {
        return new SettingsModel(1990, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                new BigDecimal("5000"), 21, new BigDecimal("0.10"), true, new BigDecimal("20000"), new BigDecimal("1000"),
                new BigDecimal("300"));
    }

    private static IncomeModel income() {
        return new IncomeModel("inc_1", "Salaire", new BigDecimal("3000"), "2026-01-01", "2048-12-31",
                new BigDecimal("0.01"), "cat_1", null);
    }

    private static BudgetDataModel snapshot() {
        return new BudgetDataModel(settings(), List.of(income()), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    private static OverviewResultModel overviewResult() {
        return new OverviewResultModel(List.of(), List.of(), null, false, 0, null, null, null, null, null,
                null, null, null);
    }

    private static AnalyseResultModel analyseResult() {
        return new AnalyseResultModel(null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("BudgetFacadeView.from(null, ...) retourne null")
    void fromNullSnapshotReturnsNull() {
        assertNull(BudgetFacadeView.from(null, ObjectifsParameters.defaults()));
    }

    @Test
    @DisplayName("BudgetFacadeView.from(...) applique les paramètres Objectifs par défaut si absents")
    void fromAppliesObjectifsDefaults() {
        BudgetFacadeView view = BudgetFacadeView.from(snapshot(), null);

        assertEquals(ObjectifsParameters.defaults(), view.objectifsParameters());
    }

    @Test
    @DisplayName("toBudgetDataDto(vue) réinjecte les paramètres Objectifs dans settings")
    void budgetDataDtoFromViewInjectsObjectifsParameters() {
        BudgetFacadeView view = BudgetFacadeView.from(snapshot(), new ObjectifsParameters(6, 2));

        BudgetDataDto dto = overviewMapper.toBudgetDataDto(view);

        assertNotNull(dto.getSettings());
        assertEquals(6, dto.getSettings().getGoalSecureHorizonMonths());
        assertEquals(2, dto.getSettings().getGoalLiquidHorizonMonths());
        assertEquals(1, dto.getIncomes().size());
        assertEquals("inc_1", dto.getIncomes().get(0).getId());
    }

    @Test
    @DisplayName("toBudgetDataDto(snapshot, objectifs) et toBudgetDataDto(vue) produisent le même DTO")
    void snapshotAndViewProduceSameDto() {
        ObjectifsParameters objectifs = new ObjectifsParameters(9, 4);

        BudgetDataDto fromSnapshot = overviewMapper.toBudgetDataDto(snapshot(), objectifs);
        BudgetDataDto fromView = overviewMapper.toBudgetDataDto(BudgetFacadeView.from(snapshot(), objectifs));

        assertEquals(fromSnapshot, fromView);
    }

    @Test
    @DisplayName("toBudgetDataDto(vue null) retourne null")
    void budgetDataDtoFromNullViewReturnsNull() {
        assertNull(overviewMapper.toBudgetDataDto((BudgetFacadeView) null));
    }

    @Test
    @DisplayName("OverviewMapper.toDto(résultat, vue) expose le budget dans data")
    void overviewDtoExposesFacadeData() {
        BudgetFacadeView view = BudgetFacadeView.from(snapshot(), ObjectifsParameters.defaults());

        OverviewResponseDto dto = overviewMapper.toDto(overviewResult(), view);

        assertNotNull(dto.getData());
        assertEquals(1, dto.getData().getIncomes().size());
    }

    @Test
    @DisplayName("OverviewMapper.toDto(résultat) sans vue n'expose pas data")
    void overviewDtoWithoutViewHasNoData() {
        OverviewResponseDto dto = overviewMapper.toDto(overviewResult());

        assertNull(dto.getData());
    }

    @Test
    @DisplayName("AnalyseMapper.toDto(résultat, vue) recopie les données de budget et les seuils Objectifs")
    void analyseDtoExposesFacadeData() {
        BudgetFacadeView view = BudgetFacadeView.from(snapshot(), new ObjectifsParameters(6, 2));

        AnalyseResponseDto dto = analyseMapper.toDto(analyseResult(), view);

        assertNotNull(dto.getData());
        assertEquals(dto.getData().getIncomes(), dto.getIncomes());
        assertEquals(dto.getData().getSettings(), dto.getSettings());
        assertEquals(6, dto.getSettings().getGoalSecureHorizonMonths());
        assertEquals(2, dto.getSettings().getGoalLiquidHorizonMonths());
    }

    @Test
    @DisplayName("AnalyseMapper.toDto(résultat) sans vue n'expose pas data")
    void analyseDtoWithoutViewHasNoData() {
        AnalyseResponseDto dto = analyseMapper.toDto(analyseResult());

        assertNull(dto.getData());
        assertNotNull(dto.getKpis());
    }
}
