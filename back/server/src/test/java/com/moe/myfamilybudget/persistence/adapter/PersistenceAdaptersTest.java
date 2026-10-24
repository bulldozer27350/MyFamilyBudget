package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * VT-310 -- Les adaptateurs de persistance ne sont plus verifies par de simples {@code isNotNull()} :
 * chaque test ecrit un etat via {@link PersistenceManager} (import complet ou mutation ciblee) puis relit
 * cet etat par l'adaptateur de lecture correspondant (round-trip), y compris les cas reset / import.
 *
 * <p>Les repositories sont mockes (voir {@link PersistenceManagerTestFactory}) : seule la coherence entre
 * le cache memoire et les ports de lecture est verifiee ici. La persistance reelle (JPA, redemarrage) releve
 * de VT-320.
 *
 * <p>SILO-213 (lot B) : le silo Banque/Pointage ne passe plus par le cache ; son adaptateur ({@code JpaBankStore})
 * est verifie par {@code JpaBankStoreTest} et par les tests de persistance reelle.
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

        // R-30 : les placements, l'immobilier et les categories d'actifs ne passent plus par le cache
        // global (PersistenceManager/BudgetDataModel) mais directement par le silo Patrimoine
        // (JpaWealthStore, voir PersistenceAdaptersJpaRoundTripTest) ; PatrimoinePersistenceAdapter
        // n'a donc plus de constructeur a un seul argument ici.

        // R-40 : les parametres retraite par defaut (aucune personne) ne passent plus par le cache global
        // (PersistenceManager/BudgetDataModel) mais directement par le silo Retraite (JpaRetirementStore,
        // voir PersistenceAdaptersJpaRoundTripTest.importedRetirementIsReadFromPensionTables et consorts) ;
        // RetirementPersistenceAdapter est retire.

        @Test
        @DisplayName("BudgetDataModel.getEffectiveSettings() : parametres par defaut")
        void settingsDefaults() {
            SettingsModel settings = persistenceManager.getBudgetData().getEffectiveSettings();

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

        // R-30 : le patrimoine (placements/historique, immobilier, categories d'actifs) n'est plus relu
        // depuis le cache global ici ; voir PersistenceAdaptersJpaRoundTripTest pour le round-trip reel
        // via le silo (JpaWealthStore). R-40 : meme principe pour la retraite (JpaRetirementStore).

        @Test
        @DisplayName("BudgetDataModel.getEffectiveSettings() relit les parametres importes")
        void settingsRoundTrip() {
            SettingsModel settings = persistenceManager.getBudgetData().getEffectiveSettings();

            assertThat(settings).isEqualTo(SETTINGS);
            assertThat(settings.getEffectiveBirthYear()).isEqualTo(1990);
            assertThat(settings.retireAge()).isEqualTo(62);
            assertThat(settings.startBalance()).isEqualByComparingTo("5000");
        }

        // R-40 : la verification "un second import remplace integralement le premier" s'appuyait
        // uniquement sur RetirementPersistenceAdapter (retire) ; voir
        // PersistenceAdaptersJpaRoundTripTest.secondImportReplacesFirstInDatabase pour le round-trip reel.
    }

    // =========================================================================
    // ROUND-TRIP : mutations ciblees puis lecture
    // =========================================================================

    @Nested
    @DisplayName("Round-trip apres mutation ciblee")
    class TargetedMutationRoundTrip {

        // R-40 : "updateRetirement -> RetirementPersistenceAdapter" s'appuyait uniquement sur l'adaptateur
        // retire ; voir PersistenceAdaptersJpaRoundTripTest.retirementWritesAreVisibleThroughJpaReader pour
        // le round-trip reel via le silo (JpaRetirementStore).

        // R-30 : addAssetCategory/removeAssetCategory ne passent plus par le cache global
        // (PersistenceManager) mais directement par le silo Patrimoine (JpaWealthStore) ; voir
        // PersistenceAdaptersJpaRoundTripTest.assetCategoriesAreReadBackFromDatabase et consorts pour
        // leur round-trip reel.
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

            persistenceManager.resetData();

            SettingsModel settings = persistenceManager.getBudgetData().getEffectiveSettings();
            assertThat(settings.getEffectiveBirthYear()).isEqualTo(1985);
            assertThat(settings.retireAge()).isEqualTo(64);
        }

        @Test
        @DisplayName("setBudgetData(null) remplace l'etat courant par les valeurs par defaut")
        void nullImportRestoresDefaults() {
            persistenceManager.setBudgetData(referenceData());

            persistenceManager.setBudgetData(null);

            assertThat(persistenceManager.getBudgetData().getEffectiveSettings().getEffectiveBirthYear())
                    .isEqualTo(1985);
        }

        // R-40 : "un reset puis un nouvel import reste lisible" s'appuyait uniquement sur
        // RetirementPersistenceAdapter (retire) ; voir
        // PersistenceAdaptersJpaRoundTripTest.importedRetirementIsReadFromPensionTables pour le round-trip
        // reel via le silo (JpaRetirementStore).
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
}
