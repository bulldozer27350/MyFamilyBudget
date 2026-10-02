package com.moe.myfamilybudget.server.internal.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * VT-310 -- Les adaptateurs de persistance ne sont plus verifies par de simples {@code isNotNull()} :
 * chaque test ecrit un etat via {@link PersistenceManager} (import complet ou mutation ciblee) puis relit
 * cet etat par l'adaptateur de lecture correspondant (round-trip), y compris les cas reset / import.
 *
 * <p>Les repositories sont mockes (voir {@link PersistenceManagerTestFactory}) : seule la coherence entre
 * le cache memoire et les ports de lecture est verifiee ici. La persistance reelle (JPA, redemarrage) releve
 * de VT-320.
 */
class PersistenceAdaptersTest {

    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
    }

    // =========================================================================
    // ETAT PAR DEFAUT (base vierge)
    // =========================================================================

    @Nested
    @DisplayName("Etat par defaut apres init()")
    class DefaultState {

        @Test
        @DisplayName("BudgetPersistenceAdapter : aucune ligne budgetaire")
        void budgetIsEmpty() {
            BudgetPersistenceAdapter adapter = new BudgetPersistenceAdapter(persistenceManager);

            assertThat(adapter.getIncomes()).isEmpty();
            assertThat(adapter.getCharges()).isEmpty();
            assertThat(adapter.getOneoffExpenses()).isEmpty();
            assertThat(adapter.getVariableIncomes()).isEmpty();
            assertThat(adapter.getVariableOverrides()).isEmpty();
        }

        @Test
        @DisplayName("PatrimoinePersistenceAdapter : aucun placement, bien, categorie ni virement")
        void patrimoineIsEmpty() {
            PatrimoinePersistenceAdapter adapter = new PatrimoinePersistenceAdapter(persistenceManager);

            assertThat(adapter.getPlacements()).isEmpty();
            assertThat(adapter.getRealEstate()).isEmpty();
            assertThat(adapter.getAssetCategories()).isEmpty();
            assertThat(adapter.getTransfers()).isEmpty();
        }

        @Test
        @DisplayName("RetirementPersistenceAdapter : parametres retraite par defaut, aucune personne")
        void retirementDefaults() {
            RetirementModel retirement = new RetirementPersistenceAdapter(persistenceManager).getRetirement();

            assertThat(retirement.people()).isEmpty();
            assertThat(retirement.pass2026()).isEqualByComparingTo("47100");
            assertThat(retirement.passGrowthRate()).isEqualByComparingTo("0.015");
            assertThat(retirement.agircPointValue()).isEqualByComparingTo("1.4386");
            assertThat(retirement.agircPointDateGlobal()).isEqualTo("2025-11-01");
            assertThat(retirement.agircPointGrowthRate()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("TaxPersistenceAdapter : bareme par defaut en 5 tranches, sans enfant ni surcharge")
        void taxDefaults() {
            TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);

            assertThat(adapter.getTaxChildren()).isEmpty();
            assertThat(adapter.getTaxRateOverrides()).isEmpty();
            assertThat(adapter.getTaxActualOverrides()).isEmpty();
            assertDefaultBrackets(adapter.getTaxBrackets());
        }

        @Test
        @DisplayName("BankPersistenceAdapter : import bancaire vide")
        void bankIsEmpty() {
            BankImportModel bank = new BankPersistenceAdapter(persistenceManager).getBankImport();

            assertThat(bank.transactions()).isEmpty();
            assertThat(bank.categories()).isEmpty();
            assertThat(bank.matchings()).isEmpty();
            assertThat(bank.pendingOperations()).isEmpty();
        }

        @Test
        @DisplayName("Loan et Goal : aucune donnee")
        void loansAndGoalsAreEmpty() {
            assertThat(new LoanPersistenceAdapter(persistenceManager).getLoans()).isEmpty();
            assertThat(new GoalPersistenceAdapter(persistenceManager).getGoals()).isEmpty();
        }

        @Test
        @DisplayName("SettingsPersistenceAdapter : parametres par defaut")
        void settingsDefaults() {
            SettingsModel settings = new SettingsPersistenceAdapter(persistenceManager).getSettings();

            assertThat(settings.getEffectiveBirthYear()).isEqualTo(1985);
            assertThat(settings.retireAge()).isEqualTo(64);
            assertThat(settings.simulateUntilAge()).isEqualTo(85);
            assertThat(settings.inflationRate()).isEqualByComparingTo("0.02");
            assertThat(settings.pivotMode()).isEqualTo("manual");
            assertThat(settings.startBalance()).isEqualByComparingTo("0");
            assertThat(settings.childExitAge()).isEqualTo(21);
            assertThat(settings.taxAbattement()).isEqualByComparingTo("0.10");
        }
    }

    // =========================================================================
    // ROUND-TRIP : import complet (setBudgetData) puis lecture par chaque adaptateur
    // =========================================================================

    @Nested
    @DisplayName("Round-trip apres import complet")
    class FullImportRoundTrip {

        @BeforeEach
        void importReferenceData() {
            persistenceManager.setBudgetData(referenceData());
        }

        @Test
        @DisplayName("BudgetPersistenceAdapter relit revenus, charges, depenses ponctuelles et variables")
        void budgetRoundTrip() {
            BudgetPersistenceAdapter adapter = new BudgetPersistenceAdapter(persistenceManager);

            assertThat(adapter.getIncomes()).containsExactly(INCOME);
            assertThat(adapter.getCharges()).containsExactly(CHARGE);
            assertThat(adapter.getOneoffExpenses()).containsExactly(ONEOFF);
            assertThat(adapter.getVariableIncomes()).containsExactly(VARIABLE_INCOME);
            assertThat(adapter.getVariableOverrides()).containsExactly(VARIABLE_OVERRIDE);
        }

        @Test
        @DisplayName("PatrimoinePersistenceAdapter relit placements (avec historique), immobilier, categories, virements")
        void patrimoineRoundTrip() {
            PatrimoinePersistenceAdapter adapter = new PatrimoinePersistenceAdapter(persistenceManager);

            assertThat(adapter.getPlacements()).containsExactly(PLACEMENT);
            assertThat(adapter.getPlacements().get(0).history()).containsExactly(PLACEMENT_HISTORY);
            assertThat(adapter.getPlacements().get(0).balance()).isEqualByComparingTo("10000");
            assertThat(adapter.getRealEstate()).containsExactly(REAL_ESTATE);
            assertThat(adapter.getAssetCategories()).containsExactly(ASSET_CATEGORY);
            assertThat(adapter.getTransfers()).containsExactly(TRANSFER);
        }

        @Test
        @DisplayName("RetirementPersistenceAdapter relit la personne et les parametres retraite")
        void retirementRoundTrip() {
            RetirementModel retirement = new RetirementPersistenceAdapter(persistenceManager).getRetirement();

            assertThat(retirement).isEqualTo(RETIREMENT);
            assertThat(retirement.people()).hasSize(1);
            assertThat(retirement.people().get(0).name()).isEqualTo("Alice");
            assertThat(retirement.people().get(0).trimestresValides()).isEqualTo(140);
            assertThat(retirement.people().get(0).salaryHistory()).hasSize(3);
        }

        @Test
        @DisplayName("TaxPersistenceAdapter relit enfants, bareme personnalise et surcharges")
        void taxRoundTrip() {
            TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);

            assertThat(adapter.getTaxChildren()).containsExactly(TAX_CHILD);
            assertThat(adapter.getTaxBrackets()).containsExactlyElementsOf(CUSTOM_BRACKETS);
            assertThat(adapter.getTaxRateOverrides()).containsExactly(TAX_RATE_OVERRIDE);
            assertThat(adapter.getTaxActualOverrides()).containsExactly(TAX_ACTUAL_OVERRIDE);
        }

        @Test
        @DisplayName("BankPersistenceAdapter relit categories, transactions et pointages")
        void bankRoundTrip() {
            BankImportModel bank = new BankPersistenceAdapter(persistenceManager).getBankImport();

            assertThat(bank).isSameAs(BANK_IMPORT);
            assertThat(bank.categories()).hasSize(1);
            assertThat(bank.categories().get(0).label()).isEqualTo("Logement");
            assertThat(bank.transactions()).hasSize(1);
            assertThat(bank.transactions().get(0).amount()).isEqualByComparingTo("-800");
            assertThat(bank.transactions().get(0).categoryId()).isEqualTo("cat_loyer");
            assertThat(bank.matchings()).hasSize(1);
            assertThat(bank.matchings().get(0).month()).isEqualTo("2026-05");
            assertThat(bank.matchings().get(0).links().get(0).txIds()).containsExactly("tx_1");
        }

        @Test
        @DisplayName("LoanPersistenceAdapter et GoalPersistenceAdapter relisent prets et objectifs (avec allocations)")
        void loansAndGoalsRoundTrip() {
            assertThat(new LoanPersistenceAdapter(persistenceManager).getLoans()).containsExactly(LOAN);

            List<ObjectifModel> goals = new GoalPersistenceAdapter(persistenceManager).getGoals();
            assertThat(goals).containsExactly(GOAL);
            assertThat(goals.get(0).allocations()).containsExactly(GOAL_ALLOCATION);
        }

        @Test
        @DisplayName("SettingsPersistenceAdapter relit les parametres importes")
        void settingsRoundTrip() {
            SettingsModel settings = new SettingsPersistenceAdapter(persistenceManager).getSettings();

            assertThat(settings).isEqualTo(SETTINGS);
            assertThat(settings.getEffectiveBirthYear()).isEqualTo(1990);
            assertThat(settings.retireAge()).isEqualTo(62);
            assertThat(settings.startBalance()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("un second import remplace integralement le premier (pas de fusion)")
        void secondImportReplacesFirst() {
            IncomeModel other = new IncomeModel("inc_2", "Freelance", bd("800"), "2027-01-01", "2030-12-31",
                    bd("0"), "", "");
            persistenceManager.setBudgetData(persistenceManager.getBudgetData()
                    .withIncomes(List.of(other))
                    .withCharges(List.of())
                    .withPlacements(List.of()));

            assertThat(new BudgetPersistenceAdapter(persistenceManager).getIncomes()).containsExactly(other);
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getCharges()).isEmpty();
            assertThat(new PatrimoinePersistenceAdapter(persistenceManager).getPlacements()).isEmpty();
            // Les domaines non touches par le second import sont conserves tels quels.
            assertThat(new RetirementPersistenceAdapter(persistenceManager).getRetirement()).isEqualTo(RETIREMENT);
        }
    }

    // =========================================================================
    // ROUND-TRIP : mutations ciblees puis lecture
    // =========================================================================

    @Nested
    @DisplayName("Round-trip apres mutation ciblee")
    class TargetedMutationRoundTrip {

        @Test
        @DisplayName("updateRetirement -> RetirementPersistenceAdapter, sans toucher aux autres domaines")
        void updateRetirement() {
            persistenceManager.setBudgetData(referenceData().withRetirement(
                    new RetirementModel(List.of(), bd("47100"), bd("0.015"), bd("1.4386"), "2025-01-01", bd("0.01"))));
            RetirementPersistenceAdapter adapter = new RetirementPersistenceAdapter(persistenceManager);
            assertThat(adapter.getRetirement().people()).isEmpty();

            persistenceManager.write(m -> m.updateRetirement(RETIREMENT));

            assertThat(adapter.getRetirement()).isEqualTo(RETIREMENT);
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getIncomes()).containsExactly(INCOME);
            assertThat(new TaxPersistenceAdapter(persistenceManager).getTaxChildren()).containsExactly(TAX_CHILD);
        }

        @Test
        @DisplayName("updateTaxConfig -> TaxPersistenceAdapter relit les quatre listes")
        void updateTaxConfig() {
            TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);

            persistenceManager.write(m -> m.updateTaxConfig(List.of(TAX_CHILD), CUSTOM_BRACKETS,
                    List.of(TAX_RATE_OVERRIDE), List.of(TAX_ACTUAL_OVERRIDE)));

            assertThat(adapter.getTaxChildren()).containsExactly(TAX_CHILD);
            assertThat(adapter.getTaxBrackets()).containsExactlyElementsOf(CUSTOM_BRACKETS);
            assertThat(adapter.getTaxRateOverrides()).containsExactly(TAX_RATE_OVERRIDE);
            assertThat(adapter.getTaxActualOverrides()).containsExactly(TAX_ACTUAL_OVERRIDE);
        }

        @Test
        @DisplayName("updateTaxConfig avec des listes nulles conserve l'existant")
        void updateTaxConfigWithNullsKeepsExistingValues() {
            persistenceManager.write(m -> m.updateTaxConfig(List.of(TAX_CHILD), CUSTOM_BRACKETS,
                    List.of(TAX_RATE_OVERRIDE), List.of(TAX_ACTUAL_OVERRIDE)));

            persistenceManager.write(m -> m.updateTaxConfig(null, null, null, null));

            TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);
            assertThat(adapter.getTaxChildren()).containsExactly(TAX_CHILD);
            assertThat(adapter.getTaxBrackets()).containsExactlyElementsOf(CUSTOM_BRACKETS);
            assertThat(adapter.getTaxRateOverrides()).containsExactly(TAX_RATE_OVERRIDE);
            assertThat(adapter.getTaxActualOverrides()).containsExactly(TAX_ACTUAL_OVERRIDE);
        }

        @Test
        @DisplayName("resetDefaultTaxBrackets -> bareme par defaut, enfants et surcharges conserves")
        void resetDefaultTaxBrackets() {
            persistenceManager.write(m -> m.updateTaxConfig(List.of(TAX_CHILD), CUSTOM_BRACKETS,
                    List.of(TAX_RATE_OVERRIDE), List.of(TAX_ACTUAL_OVERRIDE)));
            TaxPersistenceAdapter adapter = new TaxPersistenceAdapter(persistenceManager);
            assertThat(adapter.getTaxBrackets()).containsExactlyElementsOf(CUSTOM_BRACKETS);

            persistenceManager.write(m -> m.resetDefaultTaxBrackets());

            assertDefaultBrackets(adapter.getTaxBrackets());
            assertThat(adapter.getTaxChildren()).containsExactly(TAX_CHILD);
            assertThat(adapter.getTaxRateOverrides()).containsExactly(TAX_RATE_OVERRIDE);
        }

        @Test
        @DisplayName("addAssetCategory / removeAssetCategory -> PatrimoinePersistenceAdapter")
        void assetCategoryAddAndRemove() {
            PatrimoinePersistenceAdapter adapter = new PatrimoinePersistenceAdapter(persistenceManager);

            persistenceManager.write(m -> m.addAssetCategory(ASSET_CATEGORY));
            assertThat(adapter.getAssetCategories()).containsExactly(ASSET_CATEGORY);

            AssetCategoryModel second = new AssetCategoryModel("cat_2", "icon2", "Livrets", "epargne", "#00ff00");
            persistenceManager.write(m -> m.addAssetCategory(second));
            assertThat(adapter.getAssetCategories()).containsExactly(ASSET_CATEGORY, second);

            persistenceManager.write(m -> m.removeAssetCategory(ASSET_CATEGORY.id()));
            assertThat(adapter.getAssetCategories()).containsExactly(second);
        }

        @Test
        @DisplayName("updateBankImport -> BankPersistenceAdapter relit l'import mis a jour")
        void updateBankImport() {
            BankPersistenceAdapter adapter = new BankPersistenceAdapter(persistenceManager);
            assertThat(adapter.getBankImport().transactions()).isEmpty();

            persistenceManager.write(m -> m.updateBankImport(BANK_IMPORT));

            assertThat(adapter.getBankImport()).isSameAs(BANK_IMPORT);
            assertThat(adapter.getBankImport().transactions()).hasSize(1);
            assertThat(persistenceManager.getBankImport()).isSameAs(BANK_IMPORT);
        }

        @Test
        @DisplayName("updateBankImport(null) est ignore : l'import existant est conserve")
        void updateBankImportNullIsIgnored() {
            persistenceManager.write(m -> m.updateBankImport(BANK_IMPORT));

            persistenceManager.write(m -> m.updateBankImport(null));

            assertThat(new BankPersistenceAdapter(persistenceManager).getBankImport()).isSameAs(BANK_IMPORT);
        }

        @Test
        @DisplayName("un meme adaptateur voit les ecritures successives (pas de copie figee a la construction)")
        void adapterReadsLiveState() {
            BudgetPersistenceAdapter adapter = new BudgetPersistenceAdapter(persistenceManager);
            assertThat(adapter.getIncomes()).isEmpty();

            persistenceManager.setBudgetData(persistenceManager.getBudgetData().withIncomes(List.of(INCOME)));
            assertThat(adapter.getIncomes()).containsExactly(INCOME);

            persistenceManager.setBudgetData(persistenceManager.getBudgetData().withIncomes(List.of()));
            assertThat(adapter.getIncomes()).isEmpty();
        }
    }

    // =========================================================================
    // RESET / IMPORT NUL
    // =========================================================================

    @Nested
    @DisplayName("Cas reset et import nul")
    class ResetAndNullImport {

        @Test
        @DisplayName("resetData ramene tous les adaptateurs a l'etat par defaut")
        void resetDataRestoresDefaults() {
            persistenceManager.setBudgetData(referenceData());
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getIncomes()).isNotEmpty();

            persistenceManager.resetData();

            assertThat(new BudgetPersistenceAdapter(persistenceManager).getIncomes()).isEmpty();
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getCharges()).isEmpty();
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getOneoffExpenses()).isEmpty();
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getVariableIncomes()).isEmpty();
            assertThat(new BudgetPersistenceAdapter(persistenceManager).getVariableOverrides()).isEmpty();
            assertThat(new PatrimoinePersistenceAdapter(persistenceManager).getPlacements()).isEmpty();
            assertThat(new PatrimoinePersistenceAdapter(persistenceManager).getRealEstate()).isEmpty();
            assertThat(new PatrimoinePersistenceAdapter(persistenceManager).getAssetCategories()).isEmpty();
            assertThat(new PatrimoinePersistenceAdapter(persistenceManager).getTransfers()).isEmpty();
            assertThat(new RetirementPersistenceAdapter(persistenceManager).getRetirement().people()).isEmpty();
            assertThat(new TaxPersistenceAdapter(persistenceManager).getTaxChildren()).isEmpty();
            assertThat(new TaxPersistenceAdapter(persistenceManager).getTaxRateOverrides()).isEmpty();
            assertThat(new TaxPersistenceAdapter(persistenceManager).getTaxActualOverrides()).isEmpty();
            assertDefaultBrackets(new TaxPersistenceAdapter(persistenceManager).getTaxBrackets());
            assertThat(new BankPersistenceAdapter(persistenceManager).getBankImport().transactions()).isEmpty();
            assertThat(new LoanPersistenceAdapter(persistenceManager).getLoans()).isEmpty();
            assertThat(new GoalPersistenceAdapter(persistenceManager).getGoals()).isEmpty();
            SettingsModel settings = new SettingsPersistenceAdapter(persistenceManager).getSettings();
            assertThat(settings.getEffectiveBirthYear()).isEqualTo(1985);
            assertThat(settings.retireAge()).isEqualTo(64);
        }

        @Test
        @DisplayName("setBudgetData(null) remplace l'etat courant par les valeurs par defaut")
        void nullImportRestoresDefaults() {
            persistenceManager.setBudgetData(referenceData());

            persistenceManager.setBudgetData(null);

            assertThat(new BudgetPersistenceAdapter(persistenceManager).getIncomes()).isEmpty();
            assertThat(new SettingsPersistenceAdapter(persistenceManager).getSettings().getEffectiveBirthYear())
                    .isEqualTo(1985);
            assertDefaultBrackets(new TaxPersistenceAdapter(persistenceManager).getTaxBrackets());
        }

        @Test
        @DisplayName("un import avec un bareme vide restitue le bareme par defaut a la lecture")
        void emptyBracketsImportReadsDefaultSchedule() {
            persistenceManager.setBudgetData(referenceData().withTaxBrackets(List.of()));

            assertDefaultBrackets(new TaxPersistenceAdapter(persistenceManager).getTaxBrackets());
        }

        @Test
        @DisplayName("un reset puis un nouvel import reste lisible par tous les adaptateurs")
        void importAfterReset() {
            persistenceManager.setBudgetData(referenceData());
            persistenceManager.resetData();

            persistenceManager.setBudgetData(referenceData());

            assertThat(new BudgetPersistenceAdapter(persistenceManager).getIncomes()).containsExactly(INCOME);
            assertThat(new PatrimoinePersistenceAdapter(persistenceManager).getPlacements()).containsExactly(PLACEMENT);
            assertThat(new RetirementPersistenceAdapter(persistenceManager).getRetirement()).isEqualTo(RETIREMENT);
            assertThat(new BankPersistenceAdapter(persistenceManager).getBankImport()).isSameAs(BANK_IMPORT);
        }
    }

    // =========================================================================
    // DONNEES DE REFERENCE
    // =========================================================================

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final SettingsModel SETTINGS = new SettingsModel(1990, 62, 90, bd("0.025"), "2026-01-01",
            "manual", bd("5000"), 23, bd("0.05"), null, null, null, null);

    private static final IncomeModel INCOME = new IncomeModel("inc_1", "Salaire", bd("3000"), "2026-01-01",
            "2053-12-31", bd("0.01"), "cat_salaire", "note revenu");

    private static final ChargeModel CHARGE = new ChargeModel("chg_1", "Loyer", bd("900"), "2026-01-01",
            "2053-12-31", bd("0.02"), "cat_loyer", "note charge");

    private static final OneOffExpenseModel ONEOFF = new OneOffExpenseModel("oo_1", "Achat voiture", "2026-06-01",
            bd("15000"), "note");

    private static final VariableIncomeModel VARIABLE_INCOME = new VariableIncomeModel("vi_1", "Prime annuelle",
            "Salaire", bd("0.1"), 2026, 2053, "Oui", null, null);

    private static final VariableOverrideModel VARIABLE_OVERRIDE = new VariableOverrideModel("vo_1",
            "Prime annuelle", 2026, bd("5000"), "Oui", null);

    private static final PlacementHistoryEntryModel PLACEMENT_HISTORY = new PlacementHistoryEntryModel("h_1",
            "2026-01-01", bd("9500"), "releve initial");

    private static final PlacementModel PLACEMENT = new PlacementModel("plc_1", "PEA", "Actions", bd("10000"),
            "2026-01-01", bd("200"), "2026-01-01", "2053-12-31", bd("0.02"), bd("0.04"), bd("0.07"), false,
            "note", null, null, null, null, null, List.of(PLACEMENT_HISTORY));

    private static final RealEstateModel REAL_ESTATE = new RealEstateModel("re_1", "Residence Principale",
            "Residence principale", bd("250000"), 2026, bd("0.015"), "");

    private static final AssetCategoryModel ASSET_CATEGORY = new AssetCategoryModel("cat_1", "icon1", "Immobilier",
            "immobilier", "#ff0000");

    private static final TransferModel TRANSFER = new TransferModel("tr_1", "PEA", "2026-03-01", bd("500"), "");

    private static final RetirementModel RETIREMENT = new RetirementModel(
            List.of(new RetirementModel.RetirementPersonModel("p_1", "Alice", 1990, "Salaire", 140, "2025-12-31",
                    List.of(new RetirementModel.SalaryHistoryModel(2023, bd("34000")),
                            new RetirementModel.SalaryHistoryModel(2024, bd("35000")),
                            new RetirementModel.SalaryHistoryModel(2025, bd("36000"))),
                    bd("2500"), bd("0.0051"), null)),
            bd("47100"), bd("0.015"), bd("1.4386"), "2025-01-01", bd("0.01"));

    private static final TaxChildModel TAX_CHILD = new TaxChildModel("tc_1", "Emma", 2015);

    private static final List<TaxBracketModel> CUSTOM_BRACKETS = List.of(
            new TaxBracketModel("tb_a", bd("10000"), bd("0")),
            new TaxBracketModel("tb_b", bd("30000"), bd("0.12")),
            new TaxBracketModel("tb_c", null, bd("0.40")));

    private static final TaxRateOverrideModel TAX_RATE_OVERRIDE = new TaxRateOverrideModel(2027, bd("0.05"));

    private static final TaxActualOverrideModel TAX_ACTUAL_OVERRIDE = new TaxActualOverrideModel(2026, bd("1800"));

    private static final BankImportModel BANK_IMPORT = new BankImportModel(
            null,
            List.of(new BankImportModel.CategoryModel("cat_loyer", "Logement", "Depense", "Non")),
            List.of(),
            List.of(new BankImportModel.BankTransactionModel("tx_1", "2026-05-05", "Paiement Loyer", "VIR",
                    bd("-800"), "cat_loyer", List.of())),
            List.of(),
            List.of(new BankImportModel.MatchingModel("2026-05",
                    List.of(new BankImportModel.MatchingLinkModel("chg_1", List.of("tx_1"))))));

    private static final LoanModel LOAN = new LoanModel("loan_1", "Pret immobilier", bd("180000"), bd("0.0185"),
            bd("950"), bd("35"), "2022-01-01", "2042-01-01", bd("200000"), 240, null);

    private static final ObjectifAllocationModel GOAL_ALLOCATION = new ObjectifAllocationModel("al_1", "plc_1",
            bd("1000"));

    private static final ObjectifModel GOAL = new ObjectifModel("goal_1", "Voyage", bd("5000"), null, "2027-06-01",
            null, "note", List.of(GOAL_ALLOCATION));

    /** Etat complet : chaque domaine porte au moins un element non trivial. */
    private BudgetDataModel referenceData() {
        return persistenceManager.getBudgetData()
                .withSettings(SETTINGS)
                .withIncomes(List.of(INCOME))
                .withCharges(List.of(CHARGE))
                .withPlacements(List.of(PLACEMENT))
                .withRealEstate(List.of(REAL_ESTATE))
                .withRetirement(RETIREMENT)
                .withTaxChildren(List.of(TAX_CHILD))
                .withTaxBrackets(CUSTOM_BRACKETS)
                .withTaxRateOverrides(List.of(TAX_RATE_OVERRIDE))
                .withTaxActualOverrides(List.of(TAX_ACTUAL_OVERRIDE))
                .withOneoff(List.of(ONEOFF))
                .withTransfers(List.of(TRANSFER))
                .withVariableIncomes(List.of(VARIABLE_INCOME))
                .withVariableOverrides(List.of(VARIABLE_OVERRIDE))
                .withBankImport(BANK_IMPORT)
                .withAssetCategories(List.of(ASSET_CATEGORY))
                .withLoans(List.of(LOAN))
                .withObjectifs(List.of(GOAL));
    }

    private static void assertDefaultBrackets(List<TaxBracketModel> brackets) {
        assertThat(brackets).hasSize(5);
        String[] ids = {"tb_1", "tb_2", "tb_3", "tb_4", "tb_5"};
        String[] upTo = {"11294", "28797", "82341", "177106", null};
        String[] rates = {"0", "0.11", "0.30", "0.41", "0.45"};
        for (int i = 0; i < ids.length; i++) {
            assertThat(brackets.get(i).id()).isEqualTo(ids[i]);
            if (upTo[i] == null) {
                assertThat(brackets.get(i).upTo()).isNull();
            } else {
                assertThat(brackets.get(i).upTo()).isEqualByComparingTo(upTo[i]);
            }
            assertThat(brackets.get(i).rate()).isEqualByComparingTo(rates[i]);
        }
    }
}
