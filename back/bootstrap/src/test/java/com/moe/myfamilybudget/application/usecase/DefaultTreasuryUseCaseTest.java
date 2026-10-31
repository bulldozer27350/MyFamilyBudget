package com.moe.myfamilybudget.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.application.usecase.treasury.CategoryOptionModel;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieResultModel;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieSuggestionModel;
import com.moe.myfamilybudget.application.usecase.treasury.TreasuryUseCase;
import com.moe.myfamilybudget.application.usecase.treasury.VariablePreviewCellModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariablePreviewModel;
import com.moe.myfamilybudget.domain.bankpointage.core.DefaultPointageCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.core.DefaultTaxCalculationService;
import com.moe.myfamilybudget.domain.treasury.core.DefaultTresorerieCalculationService;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * R-82 (meme gabarit que {@code DefaultWealthUseCaseTest}, {@code DefaultTaxUseCaseTest}) : la composition
 * de la reponse Tresorerie (anciennement testee directement sur {@code TresorerieServiceImpl}, via les
 * methodes desormais privees {@code computeTresorerie}/{@code buildCategoryOptions}) vit dans
 * {@link DefaultTreasuryUseCase}. Ces deux tests, deplaces tels quels depuis {@code TresorerieServiceImplTest},
 * passent desormais par {@link TreasuryUseCase#getTresorerie(boolean)} et par les silos en memoire plutot
 * que par une construction directe de {@code TreasuryInputFactory.Sources}.
 */
class DefaultTreasuryUseCaseTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();
    private final InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
    private final InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
    private final InMemoryTaxStore taxStore = new InMemoryTaxStore();
    private final CacheBackedAppSettings appSettings = new CacheBackedAppSettings();

    private TreasuryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DefaultTreasuryUseCase(
                new TresorerieCommandService(treasuryStore, silos -> { }, RecordingTransactionRunner.direct()),
                new DefaultRetirementCalculationService(),
                new DefaultTaxCalculationService(),
                new DefaultTresorerieCalculationService(),
                new DefaultPointageCalculationService(),
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                taxStore,
                treasuryStore,
                new PatrimoinePersistenceAdapter(new InMemoryWealthStore().getDelegate(), treasuryStore.getDelegate()),
                bankStore);
    }

    @Test
    @DisplayName("getTresorerie() construit labels, categories, suggestions et previsions a partir de donnees completes")
    void getTresorerie_withCompleteData_buildsAllFields() {
        appSettings.updateInflationRate(BigDecimal.valueOf(1000));

        com.moe.myfamilybudget.domain.treasury.model.IncomeModel salary =
                new com.moe.myfamilybudget.domain.treasury.model.IncomeModel(
                "inc_1", "Salaire", BigDecimal.valueOf(3000),
                "2026-01-01", "2049-12-31", BigDecimal.valueOf(0.01), "cat_1", "Notes"
        );

        com.moe.myfamilybudget.domain.treasury.model.ChargeModel rent =
                new com.moe.myfamilybudget.domain.treasury.model.ChargeModel(
                "ch_1", "Loyer", BigDecimal.valueOf(1000),
                "2026-01-01", "2049-12-31", BigDecimal.valueOf(0.02), "cat_2", "Loyer principal"
        );

        com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel travel =
                new com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel(
                "one_1", "Voyage", "2026-07-15", BigDecimal.valueOf(1500), "Vacances été"
        );

        com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel bonus =
                new com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel(
                "var_1", "Prime annuelle", "Salaire", BigDecimal.valueOf(0.10),
                2026, 2049, "Oui"
        );

        com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel bonusReal =
                new com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel(
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
        bankStore.replace(bankImport);

        treasuryStore.replace(
                new TresorerieSettingsModel("2026-01-01", "manual", BigDecimal.valueOf(0.02), false, null, null, null),
                List.of(salary), List.of(rent), List.of(travel), List.of(bonus), List.of(bonusReal), List.of());

        TresorerieResultModel result = useCase.getTresorerie(false);

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

        // Suggestions: Loyer budgété 1000€, réel constaté 1050€ => écart 50€ (>= 10€)
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
        // Cellule 2026 surchargée (réel) à 3500
        VariablePreviewCellModel cell2026 = preview.cells().get(0);
        assertEquals(2026, cell2026.year());
        assertEquals(new BigDecimal("3500"), cell2026.amount());
        assertTrue(cell2026.isReal());
    }

    @Test
    @DisplayName("getTresorerie() trie les catégories bancaires selon la collation française et garde l'option par défaut")
    void getTresorerie_sortsCategoryOptionsWithFrenchCollatorAndHandlesEmptyCategories() {
        BankImportModel.CategoryModel catA = new BankImportModel.CategoryModel("cat_a", "Épargne");
        BankImportModel.CategoryModel catB = new BankImportModel.CategoryModel("cat_b", "Alimentation");
        BankImportModel.CategoryModel catC = new BankImportModel.CategoryModel("cat_c", "Énergie");

        bankStore.replace(new BankImportModel(List.of(), List.of(catA, catB, catC), List.of()));

        List<CategoryOptionModel> options = useCase.getTresorerie(false).categoryOptions();

        assertEquals(4, options.size()); // 1 par défaut + 3 triées
        assertEquals("", options.get(0).value());
        assertEquals("Alimentation", options.get(1).label());
        assertEquals("Énergie", options.get(2).label());
        assertEquals("Épargne", options.get(3).label());
    }
}
