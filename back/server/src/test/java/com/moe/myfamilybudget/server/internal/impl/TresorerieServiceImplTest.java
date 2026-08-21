package com.moe.myfamilybudget.server.internal.impl;

import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.server.internal.mapper.TresorerieMapper;
import com.moe.myfamilybudget.server.internal.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TresorerieServiceImplTest {

    private TresorerieServiceImpl service;
    private TresorerieMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TresorerieMapper();
        service = new TresorerieServiceImpl(mapper);
    }

    @Test
    void getTresorerie_returnsValidResponse() {
        ResponseEntity<TresorerieResponseDto> response = service.getTresorerie(false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2049, response.getBody().getRetireYear());
        assertNotNull(response.getBody().getCategoryOptions());
        assertFalse(response.getBody().getCategoryOptions().isEmpty());
        assertEquals("", response.getBody().getCategoryOptions().get(0).getValue());
    }

    @Test
    void computeTresorerie_withCompleteData_buildsAllFields() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, BigDecimal.valueOf(1000), "2026-01-01", "manual",
                BigDecimal.valueOf(0.02), 21, BigDecimal.valueOf(0.10),
                BigDecimal.valueOf(47100), BigDecimal.valueOf(0.015)
        );

        IncomeModel salary = new IncomeModel(
                "inc_1", "Salaire", BigDecimal.valueOf(3000),
                "2026-01-01", "2049-12-31", BigDecimal.valueOf(0.01), "cat_1", "Notes"
        );

        ChargeModel rent = new ChargeModel(
                "ch_1", "Loyer", BigDecimal.valueOf(1000),
                "2026-01-01", "2049-12-31", BigDecimal.valueOf(0.02), "cat_2", "Loyer principal"
        );

        OneOffExpenseModel travel = new OneOffExpenseModel(
                "one_1", "Voyage", "2026-07-15", BigDecimal.valueOf(1500), "Vacances été"
        );

        VariableIncomeModel bonus = new VariableIncomeModel(
                "var_1", "Prime annuelle", "Salaire", BigDecimal.valueOf(0.10),
                2026, 2049, "Oui"
        );

        VariableOverrideModel bonusReal = new VariableOverrideModel(
                "ov_1", "Prime annuelle", 2026, BigDecimal.valueOf(3500), "Oui"
        );

        BankImportModel.CategoryModel cat1 = new BankImportModel.CategoryModel("cat_1", "Revenus");
        BankImportModel.CategoryModel cat2 = new BankImportModel.CategoryModel("cat_2", "Logement");
        BankImportModel bankImport = new BankImportModel(
                List.of(
                        new BankImportModel.BankTransactionModel("tx_1", "2026-01-10", "Loyer Janvier", BigDecimal.valueOf(-1050)),
                        new BankImportModel.BankTransactionModel("tx_2", "2026-02-10", "Loyer Février", BigDecimal.valueOf(-1050)),
                        new BankImportModel.BankTransactionModel("tx_3", "2026-03-10", "Loyer Mars", BigDecimal.valueOf(-1050))
                ),
                List.of(cat1, cat2),
                List.of(
                        new BankImportModel.MatchingModel("2026-01", List.of(new BankImportModel.MatchingLinkModel("ch_1", List.of("tx_1")))),
                        new BankImportModel.MatchingModel("2026-02", List.of(new BankImportModel.MatchingLinkModel("ch_1", List.of("tx_2")))),
                        new BankImportModel.MatchingModel("2026-03", List.of(new BankImportModel.MatchingLinkModel("ch_1", List.of("tx_3"))))
                )
        );

        BudgetDataModel data = new BudgetDataModel(
                settings,
                List.of(salary),
                List.of(rent),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(travel),
                List.of(),
                List.of(bonus),
                List.of(bonusReal),
                bankImport
        );

        TresorerieResultModel result = service.computeTresorerie(data, false);

        assertNotNull(result);
        assertEquals(2049, result.retireYear());
        assertEquals(1, result.incomes().size());
        assertEquals(1, result.charges().size());
        assertEquals(1, result.oneoff().size());
        assertEquals(1, result.variableIncomes().size());
        assertEquals(1, result.variableOverrides().size());

        // Labels
        assertEquals(List.of("Salaire"), result.incomeLabels());
        assertEquals(List.of("Prime annuelle"), result.variableIncomeLabels());

        // Category options
        assertEquals(3, result.categoryOptions().size());
        assertEquals("", result.categoryOptions().get(0).value());
        assertEquals("— Non liée —", result.categoryOptions().get(0).label());
        assertEquals("Logement", result.categoryOptions().get(1).label());
        assertEquals("Revenus", result.categoryOptions().get(2).label());

        // Suggestions: Loyer budgeté 1000€, réel constaté 1050€ => écart 50€ (>= 10€)
        assertFalse(result.suggestions().isEmpty());
        TresorerieSuggestionModel suggestion = result.suggestions().get(0);
        assertEquals("ch_1", suggestion.id());
        assertEquals("Loyer", suggestion.label());
        assertEquals("charge", suggestion.kind());
        assertEquals(new BigDecimal("1000.00"), suggestion.budgeted());
        assertEquals(new BigDecimal("1050.00"), suggestion.avg3m());
        assertEquals(new BigDecimal("50.00"), suggestion.ecart());
        assertEquals(new BigDecimal("5.00"), suggestion.ecartPct());

        // Variable preview
        assertFalse(result.variablePreview().isEmpty());
        VariablePreviewModel preview = result.variablePreview().get(0);
        assertEquals("Prime annuelle", preview.label());
        assertEquals(4, preview.cells().size());
        // Cell 2026 is overridden (real) with 3500
        VariablePreviewCellModel cell2026 = preview.cells().get(0);
        assertEquals(2026, cell2026.year());
        assertEquals(new BigDecimal("3500"), cell2026.amount());
        assertTrue(cell2026.isReal());
    }

    @Test
    void crudAndAdjustEndpoints_workAsExpected() {
        ResponseEntity<Object> addRes = service.addTresorerieLigne("incomes", Map.of());
        assertEquals(HttpStatus.OK, addRes.getStatusCode());

        ResponseEntity<Object> updateRes = service.updateTresorerieLigne("charges", "ch_1", Map.of());
        assertEquals(HttpStatus.OK, updateRes.getStatusCode());

        ResponseEntity<Void> removeRes = service.removeTresorerieLigne("charges", "ch_1");
        assertEquals(HttpStatus.NO_CONTENT, removeRes.getStatusCode());

        ResponseEntity<Object> adjustRes = service.applyTresorerieAjustement(
                new TresorerieAjustementRequestDto("ch_1", "charge", BigDecimal.valueOf(1050))
        );
        assertEquals(HttpStatus.OK, adjustRes.getStatusCode());
    }
}
