package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
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
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsRepository;
import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.JpaTaxStore;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.JpaBankStore;
import com.moe.myfamilybudget.domain.credit.core.persistence.JpaLoanStore;
import com.moe.myfamilybudget.domain.settings.core.persistence.JpaAppSettingsStore;
import com.moe.myfamilybudget.domain.settings.core.persistence.JpaEconomicAssumptionsSnapshotWriter;
import com.moe.myfamilybudget.domain.settings.core.persistence.JpaSimulationSettingsSnapshotWriter;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.goals.core.persistence.JpaGoalStore;
import com.moe.myfamilybudget.domain.retirement.core.persistence.JpaRetirementStore;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;

/**
 * DB-010 -- Round-trip des adaptateurs de lecture contre une vraie base (H2 en memoire, vrais repositories
 * JPA). Completait {@code PersistenceAdaptersTest} (VT-310, repositories mockes, ne prouvait que la
 * coherence du cache memoire) avant que ce dernier ne soit retire (R-61-10) : il ne testait plus que le
 * comportement interne du hub ({@code PersistenceManager}/{@code BudgetDataModel}/{@code SettingsModel}),
 * lui-meme en cours de suppression par ce decoupage.
 *
 * <p>R-61-09-b : il n'y a plus de hub ({@code PersistenceManager}/{@code BudgetDataModel}) a amorcer. Chaque
 * scenario ecrit directement dans le(s) silo(s) qu'il exerce (via les stores JPA reels, injectes depuis le
 * contexte Spring, {@link #treasuryStore()}, {@link #taxStore()}, {@link #wealthStore()},
 * {@link #retirementStore()}, etc.) puis relit par le meme store : comme ces stores n'ont plus de cache
 * memoire intermediaire (lecture directe en base a chaque appel, cf. R-20/R-30/R-40/R-42), toute donnee qui
 * n'est pas reellement stockee et relue par JPA est donc detectee sans artifice supplementaire (plus besoin
 * d'un second lecteur a cache vierge, voir {@code RestartPersistenceTest} / VT-320 pour un redemarrage Spring
 * complet).
 *
 * <p>Les montants sont compares avec un {@code compareTo} null-safe (l'echelle des {@code BigDecimal} peut changer en
 * base) et l'ordre des collections est ignore (les relations JPA ne garantissent pas l'ordre).
 *
 * <p>Base H2 dediee, pour ne pas partager d'etat avec les autres contextes de test.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:db010;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DisplayName("DB-010 -- Round-trip des adaptateurs contre une base reelle")
class PersistenceAdaptersJpaRoundTripTest {

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void startFromBlankDatabase() {
        // SILO-212 (lot B1) : les objectifs ne sont plus portes par le modele global, resetData() ne les vide plus.
        goalStore().reset();
        // SILO-213 (lot B) : idem pour l'import bancaire (table autonome, ecrite par JpaBankStore).
        bankStore().reset();
        treasuryStore().reset();
        // R-42 : idem pour la configuration fiscale (table fiscal_*, ecrite par JpaTaxStore).
        taxStore().reset();
        // R-30 : idem pour le patrimoine (placements, immobilier, categories d'actifs, table wealth_*).
        wealthStore().reset();
        // R-40 : idem pour la retraite (plan et parametres birthYear/retireAge, table pension_*).
        retirementStore().reset();
        // R-50 : idem pour la simulation et les hypotheses economiques (table app_settings, silo Parametres).
        context.getBean(AppSettingsRepository.class).deleteAll();
    }

    private JpaGoalStore goalStore() {
        return context.getBean(JpaGoalStore.class);
    }

    private JpaBankStore bankStore() {
        return context.getBean(JpaBankStore.class);
    }

    private JpaLoanStore loanStore() {
        return context.getBean(JpaLoanStore.class);
    }

    private com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore treasuryStore() {
        return context.getBean(com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore.class);
    }

    private JpaTaxStore taxStore() {
        return context.getBean(JpaTaxStore.class);
    }

    private com.moe.myfamilybudget.domain.wealth.core.persistence.JpaWealthStore wealthStore() {
        return context.getBean(com.moe.myfamilybudget.domain.wealth.core.persistence.JpaWealthStore.class);
    }

    private JpaRetirementStore retirementStore() {
        return context.getBean(JpaRetirementStore.class);
    }

    private JpaAppSettingsStore appSettingsStore() {
        return context.getBean(JpaAppSettingsStore.class);
    }

    // =========================================================================
    // Import complet
    // =========================================================================

    // R-61-09-a : fullImportIsReadBackFromDatabase (import complet -> chaque adaptateur relit) retire.
    // Testait uniquement le hub (PersistenceManager/BudgetDataModel, freshReader()/assertReferenceState()) ;
    // couverture entierement dupliquee par les tests "imported...IsReadFrom...Tables" de chaque silo plus bas
    // dans ce meme fichier (Objectifs, Credit, Fiscalite, Retraite, Import bancaire, Patrimoine, Tresorerie).

    // R-51 : settingsAreCopiedToOwnerTables et ownerSettingsTablesAreMigratedAtStartup (SILO-220 B1)
    // verifiaient la copie birthYear/retireAge/childExitAge/taxAbattement/pivotDate/pivotMode/startBalance
    // depuis le blob `settings` (SettingsEntity) vers pension_settings/fiscal_settings/cashflow_settings,
    // au moment de l'import et au demarrage si ces tables etaient vides. SettingsEntity est retire (SILO-220
    // B2, ce patch) : ce mecanisme de copie depuis `settings` n'existe plus, ces deux tests sont retires avec
    // lui. Depuis R-20/R-40/R-42, la seule ecriture encore pertinente de ces champs passe par l'API de
    // commande propre a chaque silo (ex. retirementStore().updateRetirementSetting(...)), pas par le modele
    // generique BudgetDataModel.settings()/writer.setBudgetData(...) exerce ici.

    @Test
    @DisplayName("un second import remplace le premier en base (aucune ligne orpheline)")
    void secondImportReplacesFirstInDatabase() {
        seedReferenceData();
        IncomeModel other = new IncomeModel("inc_2", "Freelance", bd("800"), "2027-01-01", "2030-12-31",
                bd("0"), "", "");

        treasuryStore().replace(REFERENCE_TREASURY_SETTINGS,
                List.of(other), List.of(), List.of(ONEOFF), List.of(VARIABLE_INCOME), List.of(VARIABLE_OVERRIDE),
                List.of(TRANSFER));
        wealthStore().replace(List.of(), List.of(REAL_ESTATE), List.of(), List.of(ASSET_CATEGORY));

        assertSameContent(treasuryStore().getIncomes(), List.of(other));
        assertThat(treasuryStore().getCharges()).isEmpty();
        assertThat(wealthStore().getPlacements()).isEmpty();
        // Les domaines non touches par le second import restent identiques.
        assertSameContent(retirementStore().getRetirement(), RETIREMENT);
    }

    // =========================================================================
    // Mutations ciblees
    // =========================================================================

    @Test
    @DisplayName("updateRetirement -> relu en base, sans toucher aux autres domaines")
    void updateRetirementIsReadBackFromDatabase() {
        seedReferenceData();
        // R-40 : updateRetirement passe desormais par le silo reel (JpaRetirementStore), plus par le hub
        // (PersistenceManager.write ne l'atteint plus : syncPension a ete retire). Meme principe que
        // taxStore().updateTaxConfig(...) ci-dessous pour la Fiscalite (R-42). Un premier retraitement
        // distinct de RETIREMENT prouve que le second (la valeur finale attendue) la remplace bien.
        retirementStore().updateRetirement(
                new RetirementModel(List.of(), bd("47100"), bd("0.015"), bd("1.4386"), "2025-01-01", bd("0.01")));

        retirementStore().updateRetirement(RETIREMENT);

        assertSameContent(retirementStore().getRetirement(), RETIREMENT);
        assertSameContent(treasuryStore().getIncomes(), List.of(INCOME));
        assertSameContent(taxStore().getTaxChildren(), List.of(TAX_CHILD));
    }

    @Test
    @DisplayName("R-42 -- updateTaxConfig puis resetDefaultTaxBrackets -> relus en base")
    void taxConfigAndBracketResetAreReadBackFromDatabase() {
        taxStore().updateTaxConfig(List.of(TAX_CHILD), CUSTOM_BRACKETS, List.of(TAX_RATE_OVERRIDE),
                List.of(TAX_ACTUAL_OVERRIDE));

        assertSameContent(taxStore().getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(taxStore().getTaxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(taxStore().getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(taxStore().getTaxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));

        taxStore().resetDefaultTaxBrackets();

        assertDefaultBrackets(taxStore().getTaxBrackets());
        assertSameContent(taxStore().getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(taxStore().getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
    }

    @Test
    @DisplayName("addAssetCategory / removeAssetCategory -> relus en base")
    void assetCategoriesAreReadBackFromDatabase() {
        AssetCategoryModel second = new AssetCategoryModel("cat_2", "icon2", "Livrets", "epargne", "#00ff00");

        wealthStore().addAssetCategory(ASSET_CATEGORY);
        wealthStore().addAssetCategory(second);
        assertSameContent(wealthStore().getAssetCategories(), List.of(ASSET_CATEGORY, second));

        wealthStore().removeAssetCategory(ASSET_CATEGORY.id());
        assertSameContent(wealthStore().getAssetCategories(), List.of(second));
    }

    @Test
    @DisplayName("updateBankImport -> transactions, categories et pointages relus en base")
    void bankImportIsReadBackFromDatabase() {
        bankStore().updateBankImport(BANK_IMPORT);

        BankImportModel bank = bankStore().getBankImport();

        assertSameContent(bank.categories(), BANK_IMPORT.categories());
        assertSameContent(bank.transactions(), BANK_IMPORT.transactions());
        assertSameContent(bank.matchings(), BANK_IMPORT.matchings());
    }

    // =========================================================================
    // Reset
    // =========================================================================

    // R-61-09-a : resetIsReadBackFromDatabase et importAfterResetIsReadBackFromDatabase retires. Testaient
    // uniquement le hub (writer.resetData()/freshReader()/assertReferenceState()) ; couverture du reset deja
    // assuree, silo par silo, par resetEmptiesGoalTables, resetEmptiesCreditTable,
    // resetClearsFiscalTablesAndRestoresDefaultBrackets, resetRestoresEmptyRetirementThroughJpa,
    // resetRestoresEmptyBankImportThroughJpa, resetEmptiesWealthTables et resetEmptiesCashflowTables plus bas
    // dans ce meme fichier.

    // =========================================================================
    // Cas optionnels
    // =========================================================================

    @Test
    @DisplayName("elements sans enfants (historique, allocations, personnes) -> relus vides, pas null")
    void optionalChildrenAreReadBackEmpty() {
        PlacementModel bare = new PlacementModel("plc_bare", "Livret", "Epargne", bd("1000"), "2026-01-01",
                bd("0"), null, null, bd("0.01"), bd("0.01"), bd("0.01"), null, null, null, null, null, null,
                null, List.of());
        ObjectifModel bareGoal = new ObjectifModel("goal_bare", "Reserve", bd("2000"), null, null, null, null,
                List.of());
        RetirementModel noPeople = new RetirementModel(List.of(), bd("47100"), bd("0.015"), bd("1.4386"),
                "2025-01-01", bd("0.01"));

        wealthStore().replace(List.of(bare), List.of(), List.of(), List.of());
        retirementStore().updateRetirement(noPeople);
        goalStore().replace(List.of(bareGoal));

        List<PlacementModel> placements = wealthStore().getPlacements();
        assertThat(placements).hasSize(1);
        assertThat(placements.get(0).id()).isEqualTo("plc_bare");
        assertThat(placements.get(0).history()).isNotNull().isEmpty();
        List<ObjectifModel> goals = goalStore().getGoals();
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).id()).isEqualTo("goal_bare");
        assertThat(goals.get(0).allocations()).isNotNull().isEmpty();
        assertThat(retirementStore().getRetirement().people()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("import sans bareme fiscal -> le bareme par defaut est relu")
    void emptyBracketsAreReadBackAsDefaultSchedule() {
        taxStore().updateTaxConfig(List.of(TAX_CHILD), List.of(), List.of(TAX_RATE_OVERRIDE),
                List.of(TAX_ACTUAL_OVERRIDE));

        assertDefaultBrackets(taxStore().getTaxBrackets());
    }

    // =========================================================================
    // SILO-212 (lot B1) -- Objectifs ecrits et lus directement dans les tables autonomes
    // =========================================================================

    @Test
    @DisplayName("SILO-212 -- replace -> les objectifs sont ecrits dans les tables autonomes et relus par JPA")
    void replacedGoalsAreReadFromGoalTables() {
        goalStore().replace(List.of(GOAL));

        assertThat(context.getBean(GoalRepository.class).count()).isEqualTo(1);
        List<ObjectifModel> goals = goalStore().getGoals();
        assertSameContent(goals, List.of(GOAL));
        assertThat(goals.get(0).allocations()).hasSize(1);
    }

    @Test
    @DisplayName("SILO-212 -- creation, mise a jour et suppression d'un objectif sont visibles via la lecture JPA")
    void goalWritesAreVisibleThroughJpaReader() {
        JpaGoalStore store = goalStore();
        store.replace(List.of(GOAL));

        store.saveGoalRow(Map.of("id", "goal_2", "label", "Voiture", "targetAmount", bd("8000"),
                "targetDate", "2028-01-01"));
        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1", "goal_2");

        store.saveGoalRow(Map.of("id", "goal_2", "label", "Voiture neuve", "targetAmount", bd("9000"),
                "targetDate", "2028-01-01"));
        assertThat(store.getGoals()).filteredOn(g -> "goal_2".equals(g.id()))
                .singleElement()
                .satisfies(g -> assertThat(g.label()).isEqualTo("Voiture neuve"));
        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_1", "goal_2");

        store.deleteGoalRow("goal_1");
        assertThat(store.getGoals()).extracting(ObjectifModel::id).containsExactly("goal_2");
    }

    @Test
    @DisplayName("SILO-212 -- un objectif sans identifiant recoit un identifiant genere et ses allocations sont relues")
    void goalWithoutIdGetsGeneratedIdAndKeepsAllocations() {
        Map<String, Object> saved = goalStore().saveGoalRow(Map.of("label", "Reserve", "targetAmount", bd("1500"),
                "allocations", List.of(Map.of("placementId", "plc_1", "amount", "250"))));

        String id = String.valueOf(saved.get("id"));
        assertThat(id).startsWith("pat_");
        List<ObjectifModel> goals = goalStore().getGoals();
        assertThat(goals).singleElement().satisfies(g -> {
            assertThat(g.id()).isEqualTo(id);
            assertThat(g.getEffectiveAllocations()).singleElement().satisfies(allocation -> {
                assertThat(allocation.id()).isNotBlank();
                assertThat(allocation.placementId()).isEqualTo("plc_1");
                assertThat(allocation.amount()).isEqualByComparingTo("250");
            });
        });
    }

    // R-61-09-a : goalsAreReloadedFromGoalTablesOnStartup et globalModelWriteDoesNotTouchGoalTables retires.
    // Le premier relisait le modele global via freshReader() en plus de goalStore() (deja verifie par
    // replacedGoalsAreReadFromGoalTables) ; le second prouvait l'absence d'effet d'une ecriture du hub sur
    // les tables Objectifs, garantie devenue structurellement impossible a violer (plus de modele global
    // capable d'ecrire ces tables) -- meme raisonnement que le 3e scenario ecarte en R-61-05.

    @Test
    @DisplayName("SILO-212 -- reset -> les tables autonomes sont videes")
    void resetEmptiesGoalTables() {
        goalStore().replace(List.of(GOAL));

        goalStore().reset();

        assertThat(context.getBean(GoalRepository.class).count()).isZero();
        assertThat(goalStore().getGoals()).isEmpty();
    }

    // =========================================================================
    // SILO-214 -- Prets ecrits et lus directement dans leur table (JpaLoanStore)
    // =========================================================================

    @Test
    @DisplayName("SILO-214 -- creation, mise a jour et suppression d'un pret sont relues depuis la table autonome")
    void loanWritesAreReadBackFromCreditTable() {
        loanStore().replace(List.of(LOAN));

        loanStore().saveLoanRow(Map.of("id", "loan_2", "label", "Pret auto", "crd", bd("12000"),
                "rate", bd("0.02512"), "monthly", bd("300")));
        assertThat(loanStore().getLoans()).extracting(LoanModel::id).containsExactly("loan_1", "loan_2");
        assertThat(loanStore().getLoans()).filteredOn(l -> "loan_2".equals(l.id()))
                .singleElement()
                .satisfies(l -> assertThat(l.rate()).isEqualByComparingTo("0.02512"));

        loanStore().saveLoanRow(Map.of("id", "loan_2", "label", "Pret auto solde", "crd", bd("0"),
                "rate", bd("0.02512"), "monthly", bd("300")));
        assertThat(loanStore().getLoans()).filteredOn(l -> "loan_2".equals(l.id()))
                .singleElement()
                .satisfies(l -> assertThat(l.label()).isEqualTo("Pret auto solde"));

        loanStore().deleteLoanRow("loan_1");
        assertThat(loanStore().getLoans()).extracting(LoanModel::id).containsExactly("loan_2");
        assertThat(context.getBean(CreditLoanRepository.class).count()).isEqualTo(1);
    }

    // R-61-09-a : loansSurviveCacheRestart et globalModelWriteDoesNotTouchCreditTable retires. Meme
    // raisonnement que pour les Objectifs ci-dessus (voir note R-61-09-a plus haut) : "survivre au
    // redemarrage du cache" n'a plus d'objet sans cache hub, et plus aucune ecriture du modele global ne
    // peut atteindre la table des prets.

    @Test
    @DisplayName("SILO-214 -- reset -> la table autonome est videe")
    void resetEmptiesCreditTable() {
        loanStore().replace(List.of(LOAN));

        loanStore().reset();

        assertThat(context.getBean(CreditLoanRepository.class).count()).isZero();
        assertThat(loanStore().getLoans()).isEmpty();
    }

    // =========================================================================
    // R-50 -- Simulation et hypotheses economiques ecrites et lues directement dans app_settings
    // =========================================================================

    @Test
    @DisplayName("R-50 -- sans ligne : valeurs par defaut ; ecritures relues depuis la table autonome")
    void appSettingsAreWrittenAndReadFromTheirOwnTable() {
        assertThat(appSettingsStore().getSimulationSettings().simulateUntilAge()).isEqualTo(85);
        assertThat(appSettingsStore().getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(context.getBean(AppSettingsRepository.class).count()).isZero();

        appSettingsStore().updateSimulateUntilAge(92);
        appSettingsStore().updateInflationRate(bd("0.01750001"));

        assertThat(context.getBean(AppSettingsRepository.class).count()).isEqualTo(1);
        assertThat(appSettingsStore().getSimulationSettings().simulateUntilAge()).isEqualTo(92);
        assertThat(appSettingsStore().getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.01750001");
    }

    // R-61-09-a : appSettingsSurviveCacheRestart et globalModelWriteDoesNotTouchAppSettings retires. Meme
    // raisonnement que pour les Objectifs et le Credit ci-dessus (voir note R-61-09-a plus haut).

    @Test
    @DisplayName("R-50 -- import et reinitialisation par les ports de transition")
    void snapshotWritersReplaceAndReset() {
        JpaSimulationSettingsSnapshotWriter simulation = context.getBean(JpaSimulationSettingsSnapshotWriter.class);
        JpaEconomicAssumptionsSnapshotWriter economic = context.getBean(JpaEconomicAssumptionsSnapshotWriter.class);

        simulation.replace(new SimulationSettingsModel(95));
        economic.replace(new EconomicAssumptionsModel(bd("0.04")));
        assertThat(appSettingsStore().getSimulationSettings().simulateUntilAge()).isEqualTo(95);
        assertThat(appSettingsStore().getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.04");

        simulation.reset();
        economic.reset();
        assertThat(appSettingsStore().getSimulationSettings().simulateUntilAge()).isEqualTo(85);
        assertThat(appSettingsStore().getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(context.getBean(AppSettingsRepository.class).count()).isEqualTo(1);
    }

    // =========================================================================
    // DB-1011 -- Fiscalite lue depuis les tables autonomes
    // =========================================================================

    @Test
    @DisplayName("R-42 -- import -> la fiscalite est recopiee dans les tables autonomes et relue par JpaTaxStore")
    void importedTaxConfigIsReadFromFiscalTables() {
        seedReferenceData();

        assertThat(context.getBean(FiscalChildRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(FiscalBracketRepository.class).count()).isEqualTo(CUSTOM_BRACKETS.size());
        assertSameContent(taxStore().getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(taxStore().getTaxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(taxStore().getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(taxStore().getTaxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));
    }

    @Test
    @DisplayName("R-42 -- updateTaxConfig et resetDefaultTaxBrackets sont visibles via JpaTaxStore")
    void taxWritesAreVisibleThroughJpaReader() {
        seedReferenceData();
        TaxChildModel secondChild = new TaxChildModel("tc_2", "Lucas", 2018);

        taxStore().updateTaxConfig(List.of(TAX_CHILD, secondChild), CUSTOM_BRACKETS, List.of(), List.of());

        assertThat(taxStore().getTaxChildren()).extracting(TaxChildModel::id).containsExactly("tc_1", "tc_2");
        assertThat(taxStore().getTaxRateOverrides()).isEmpty();
        assertThat(taxStore().getTaxActualOverrides()).isEmpty();
        assertSameContent(taxStore().getTaxBrackets(), CUSTOM_BRACKETS);

        taxStore().resetDefaultTaxBrackets();

        assertDefaultBrackets(taxStore().getTaxBrackets());
        assertThat(taxStore().getTaxChildren()).extracting(TaxChildModel::id).containsExactly("tc_1", "tc_2");
    }

    @Test
    @DisplayName("R-42 -- import sans bareme -> le bareme par defaut est ecrit puis relu par JpaTaxStore")
    void emptyBracketsAreReadAsDefaultScheduleThroughJpa() {
        taxStore().updateTaxConfig(List.of(TAX_CHILD), List.of(), List.of(TAX_RATE_OVERRIDE),
                List.of(TAX_ACTUAL_OVERRIDE));

        assertDefaultBrackets(taxStore().getTaxBrackets());
    }

    // R-61-09-a : fiscalDataIsReloadedFromFiscalTablesOnStartup retire. La relecture via le modele global
    // (freshReader()) n'a plus d'objet sans cache hub ; la relecture via taxStore() est deja prouvee par
    // importedTaxConfigIsReadFromFiscalTables ci-dessus.

    @Test
    @DisplayName("R-42 -- reinitialisation -> listes videes et bareme par defaut relu par JpaTaxStore")
    void resetClearsFiscalTablesAndRestoresDefaultBrackets() {
        seedReferenceData();

        taxStore().reset();

        assertThat(taxStore().getTaxChildren()).isEmpty();
        assertThat(taxStore().getTaxRateOverrides()).isEmpty();
        assertThat(taxStore().getTaxActualOverrides()).isEmpty();
        assertDefaultBrackets(taxStore().getTaxBrackets());
    }

    // =========================================================================
    // DB-1001 -- Retraite lue depuis les tables autonomes
    // =========================================================================

    @Test
    @DisplayName("DB-1001 -- import -> la retraite est recopiee dans les tables autonomes et relue par JPA")
    void importedRetirementIsReadFromPensionTables() {
        seedReferenceData();

        assertThat(context.getBean(PensionPlanRepository.class).count()).isEqualTo(1);
        RetirementModel retirement = retirementStore().getRetirement();
        assertSameContent(retirement, RETIREMENT);
        assertThat(retirement.people().get(0).salaryHistory()).extracting(h -> h.year())
                .containsExactly(2023, 2024, 2025);
    }

    @Test
    @DisplayName("DB-1001 -- updateRetirement est visible via la lecture JPA (un seul plan, ancien contenu remplace)")
    void retirementWritesAreVisibleThroughJpaReader() {
        seedReferenceData();
        JpaRetirementStore adapter = retirementStore();
        RetirementModel updated = new RetirementModel(
                List.of(new RetirementModel.RetirementPersonModel("p_2", "Bob", 1988, "Salaire", 120, "2025-12-31",
                        List.of(new RetirementModel.SalaryHistoryModel(2025, bd("41000"))),
                        bd("1800"), bd("0.00512345"), null)),
                bd("47100"), bd("0.015"), bd("1.4386"), "2025-01-01", bd("0.01"));

        adapter.updateRetirement(updated);

        assertThat(context.getBean(PensionPlanRepository.class).count()).isEqualTo(1);
        assertSameContent(adapter.getRetirement(), updated);
        RetirementModel reread = retirementStore().getRetirement();
        assertSameContent(reread, updated);
        assertThat(reread.people().get(0).ratioPointsParEuro()).isEqualByComparingTo("0.00512345");
    }

    // R-61-09-a : retirementIsReloadedFromPensionTablesOnStartup retire. La relecture via le modele global
    // (freshReader()) n'a plus d'objet sans cache hub ; la relecture via retirementStore() est deja prouvee
    // par importedRetirementIsReadFromPensionTables ci-dessus.

    @Test
    @DisplayName("DB-1001 -- reinitialisation -> la retraite par defaut (sans personne) est relue par JPA")
    void resetRestoresEmptyRetirementThroughJpa() {
        seedReferenceData();

        retirementStore().reset();

        assertThat(context.getBean(PensionPlanRepository.class).count()).isLessThanOrEqualTo(1);
        assertThat(retirementStore().getRetirement().people()).isEmpty();
    }

    // =========================================================================
    // DB-1031 -- Import bancaire lu depuis la table autonome
    // =========================================================================

    @Test
    @DisplayName("DB-1031 -- import -> l'import bancaire est ecrit dans la table autonome et relu par JPA")
    void importedBankImportIsReadFromDocumentTable() {
        bankStore().replace(BANK_IMPORT);

        assertThat(context.getBean(BankImportDocumentRepository.class).count()).isEqualTo(1);
        BankImportModel bank = bankStore().getBankImport();
        assertSameContent(bank.categories(), BANK_IMPORT.categories());
        assertSameContent(bank.transactions(), BANK_IMPORT.transactions());
        assertSameContent(bank.matchings(), BANK_IMPORT.matchings());
    }

    @Test
    @DisplayName("DB-1031 -- updateBankImport remplace le document (une seule ligne) et est visible via la lecture JPA")
    void bankWritesAreVisibleThroughJpaReader() {
        bankStore().replace(BANK_IMPORT);
        JpaBankStore adapter = bankStore();
        BankImportModel updated = new BankImportModel(
                null,
                BANK_IMPORT.categories(),
                List.of(),
                List.of(new BankImportModel.BankTransactionModel("tx_2", "2026-06-01", "Cafe été", "CB",
                        bd("-3.5"), "cat_loyer", List.of())),
                List.of(),
                List.of());

        adapter.updateBankImport(updated);

        assertThat(context.getBean(BankImportDocumentRepository.class).count()).isEqualTo(1);
        assertThat(adapter.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_2");
        BankImportModel reread = bankStore().getBankImport();
        assertSameContent(reread.transactions(), updated.transactions());
        assertThat(reread.matchings()).isEmpty();
    }

    @Test
    @DisplayName("DB-1031 -- 3 000 transactions -> relues intactes par JPA")
    void largeBankImportIsReadThroughJpa() {
        List<BankImportModel.BankTransactionModel> many = new java.util.ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            many.add(new BankImportModel.BankTransactionModel("tx_" + i, "2026-05-05", "Libelle " + i, "CB",
                    bd("-" + (i + 1)), "cat_loyer", List.of()));
        }
        bankStore().replace(new BankImportModel(
                null, BANK_IMPORT.categories(), List.of(), many, List.of(), List.of()));

        List<BankImportModel.BankTransactionModel> reread = bankStore().getBankImport().transactions();
        assertThat(reread).hasSize(3000);
        assertThat(reread.get(2999).id()).isEqualTo("tx_2999");
    }

    // R-61-09-a : bankImportIsReloadedFromDocumentTableOnStartup retire. La relecture via le modele global
    // (freshReader()) n'a plus d'objet sans cache hub ; la relecture via bankStore() est deja prouvee par
    // importedBankImportIsReadFromDocumentTable ci-dessus.

    @Test
    @DisplayName("DB-1031 -- reinitialisation -> un import vide (jamais null) est relu par JPA")
    void resetRestoresEmptyBankImportThroughJpa() {
        bankStore().replace(BANK_IMPORT);

        bankStore().reset();

        BankImportModel bank = bankStore().getBankImport();
        assertThat(bank).isNotNull();
        assertThat(bank.transactions()).isEmpty();
        assertThat(bank.categories()).isEmpty();
    }

    @Test
    @DisplayName("DB-1031 -- table autonome sans document -> un import vide est restitue")
    void missingDocumentIsReadAsEmptyImport() {
        bankStore().replace(BANK_IMPORT);
        context.getBean(BankImportDocumentRepository.class).deleteAll();

        BankImportModel bank = bankStore().getBankImport();

        assertThat(bank).isNotNull();
        assertThat(bank.transactions()).isEmpty();
    }

    // =========================================================================
    // DB-1051 / R-30 -- Patrimoine ecrit et lu directement sur les tables autonomes (silo)
    // =========================================================================

    @Test
    @DisplayName("R-30 -- import -> placements, immobilier et categories recopies dans les tables autonomes et relus par JPA")
    void importedWealthIsReadFromWealthTables() {
        seedReferenceData();

        assertThat(context.getBean(WealthPlacementRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(WealthRealEstateRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(WealthCategoryRepository.class).count()).isEqualTo(1);

        assertSameContent(wealthStore().getPlacements(), List.of(PLACEMENT));
        assertThat(wealthStore().getPlacements().get(0).history()).hasSize(1);
        assertSameContent(wealthStore().getRealEstate(), List.of(REAL_ESTATE));
        assertSameContent(wealthStore().getAssetCategories(), List.of(ASSET_CATEGORY));
    }

    @Test
    @DisplayName("R-30 -- creation, mise a jour et suppression d'un placement sont visibles via la lecture JPA")
    void placementWritesAreVisibleThroughJpaReader() {
        seedReferenceData();

        wealthStore().savePatrimoineRow(PatrimoineList.PLACEMENTS, Map.of("id", "plc_2", "label", "Livret A",
                "category", "Epargne", "balance", bd("5000"), "ratePess", bd("0.02512")));
        assertThat(wealthStore().getPlacements()).extracting(PlacementModel::id)
                .containsExactlyInAnyOrder("plc_1", "plc_2");
        assertThat(wealthStore().getPlacements()).filteredOn(p -> "plc_2".equals(p.id()))
                .singleElement()
                .satisfies(p -> assertThat(p.ratePess()).isEqualByComparingTo("0.02512"));

        wealthStore().savePatrimoineRow(PatrimoineList.PLACEMENTS, Map.of("id", "plc_2", "label", "Livret A renomme",
                "category", "Epargne", "balance", bd("5200")));
        assertThat(wealthStore().getPlacements()).filteredOn(p -> "plc_2".equals(p.id()))
                .singleElement()
                .satisfies(p -> assertThat(p.label()).isEqualTo("Livret A renomme"));

        wealthStore().deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");
        assertThat(wealthStore().getPlacements()).extracting(PlacementModel::id)
                .containsExactly("plc_2");
    }

    @Test
    @DisplayName("R-30 -- l'historique d'un placement (ajout, mise a jour, suppression) est visible via la lecture JPA")
    void placementHistoryWritesAreVisibleThroughJpaReader() {
        seedReferenceData();

        Map<String, Object> added = wealthStore().addPlacementHistoryEntry("plc_1",
                Map.of("date", "2026-06-01", "value", bd("10400"), "notes", "releve juin"));
        String entryId = String.valueOf(added.get("id"));
        assertThat(wealthStore().getPlacements().get(0).history()).extracting(PlacementHistoryEntryModel::id)
                .containsExactlyInAnyOrder("h_1", entryId);

        wealthStore().updatePlacementHistoryEntry("plc_1", entryId, Map.of("value", bd("10450")));
        assertThat(wealthStore().getPlacements().get(0).history())
                .filteredOn(h -> entryId.equals(h.id()))
                .singleElement()
                .satisfies(h -> assertThat(h.value()).isEqualByComparingTo("10450"));

        wealthStore().deletePlacementHistoryEntry("plc_1", "h_1");
        assertThat(wealthStore().getPlacements().get(0).history())
                .extracting(PlacementHistoryEntryModel::id).containsExactly(entryId);
    }

    @Test
    @DisplayName("R-30 -- biens immobiliers et categories d'actifs : ecritures visibles via la lecture JPA")
    void realEstateAndCategoryWritesAreVisibleThroughJpaReader() {
        seedReferenceData();

        wealthStore().savePatrimoineRow(PatrimoineList.REAL_ESTATE, Map.of("id", "re_2", "label", "Studio",
                "currentValue", bd("90000")));
        assertThat(wealthStore().getRealEstate()).extracting(RealEstateModel::id)
                .containsExactlyInAnyOrder("re_1", "re_2");
        wealthStore().deletePatrimoineRow(PatrimoineList.REAL_ESTATE, "re_1");
        assertThat(wealthStore().getRealEstate()).extracting(RealEstateModel::id)
                .containsExactly("re_2");

        AssetCategoryModel second = new AssetCategoryModel("cat_2", "icon2", "Livrets", "epargne", "#00ff00");
        wealthStore().addAssetCategory(second);
        assertSameContent(wealthStore().getAssetCategories(), List.of(ASSET_CATEGORY, second));
        wealthStore().removeAssetCategory("cat_1");
        assertSameContent(wealthStore().getAssetCategories(), List.of(second));
    }

    @Test
    @DisplayName("R-30 -- reinitialisation -> les tables autonomes sont videes")
    void resetEmptiesWealthTables() {
        seedReferenceData();

        wealthStore().reset();

        assertThat(context.getBean(WealthPlacementRepository.class).count()).isZero();
        assertThat(context.getBean(WealthRealEstateRepository.class).count()).isZero();
        assertThat(context.getBean(WealthCategoryRepository.class).count()).isZero();
        assertThat(wealthStore().getPlacements()).isEmpty();
        assertThat(wealthStore().getRealEstate()).isEmpty();
        assertThat(wealthStore().getAssetCategories()).isEmpty();
    }

    // =========================================================================
    // DB-1061 -- Tresorerie lue depuis les tables autonomes
    // =========================================================================

    @Test
    @DisplayName("DB-1061 -- import -> les six listes de tresorerie sont recopiees dans les tables autonomes et relues par JPA")
    void importedCashflowIsReadFromCashflowTables() {
        seedReferenceData();

        assertThat(context.getBean(CashflowIncomeRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowChargeRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowOneOffRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowTransferRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowVariableIncomeRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowVariableOverrideRepository.class).count()).isEqualTo(1);

        com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore budget = treasuryStore();
        assertSameContent(budget.getIncomes(), List.of(INCOME));
        assertSameContent(budget.getCharges(), List.of(CHARGE));
        assertSameContent(budget.getOneoffExpenses(), List.of(ONEOFF));
        assertSameContent(budget.getVariableIncomes(), List.of(VARIABLE_INCOME));
        assertSameContent(budget.getVariableOverrides(), List.of(VARIABLE_OVERRIDE));
        assertSameContent(budget.getTransfers(), List.of(TRANSFER));
    }

    @Test
    @DisplayName("DB-1061 -- ajout, mise a jour et suppression d'une ligne de tresorerie sont visibles via JpaTreasuryStore")
    void cashflowWritesAreVisibleThroughJpaReader() {
        seedReferenceData();
        com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore store =
                context.getBean(com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore.class);

        store.addTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.INCOMES,
                Map.of("id", "inc_2", "label", "Freelance", "monthly", bd("800")));
        assertThat(store.getIncomes()).extracting(IncomeModel::id)
                .containsExactlyInAnyOrder("inc_1", "inc_2");

        store.updateTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.INCOMES, "inc_2",
                com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField.MONTHLY, bd("950"));
        assertThat(store.getIncomes()).filteredOn(i -> "inc_2".equals(i.id()))
                .singleElement()
                .satisfies(i -> assertThat(i.monthly()).isEqualByComparingTo("950"));

        store.removeTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.INCOMES, "inc_1");
        assertThat(store.getIncomes()).extracting(IncomeModel::id)
                .containsExactly("inc_2");
    }

    @Test
    @DisplayName("DB-1061 / DA-14 -- virements : ecritures de virement via JpaTreasuryStore")
    void transferWritesAreVisibleThroughJpaReader() {
        seedReferenceData();
        com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore store =
                context.getBean(com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore.class);

        store.addTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.TRANSFERS,
                Map.of("id", "tr_2", "amount", bd("250")));
        assertThat(store.getTransfers()).extracting(TransferModel::id)
                .containsExactlyInAnyOrder("tr_1", "tr_2");

        store.removeTresorerieRow(com.moe.myfamilybudget.domain.treasury.port.TresorerieList.TRANSFERS, "tr_1");
        assertThat(store.getTransfers()).extracting(TransferModel::id)
                .containsExactly("tr_2");
    }

    @Test
    @DisplayName("DB-1061 -- reset du silo tresorerie vide les tables de cashflow")
    void resetEmptiesCashflowTables() {
        seedReferenceData();
        com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore store =
                context.getBean(com.moe.myfamilybudget.domain.treasury.core.persistence.JpaTreasuryStore.class);

        store.reset();

        assertThat(context.getBean(CashflowIncomeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowChargeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowOneOffRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowTransferRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowVariableIncomeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowVariableOverrideRepository.class).count()).isZero();
        assertThat(store.getIncomes()).isEmpty();
        assertThat(store.getCharges()).isEmpty();
        assertThat(store.getTransfers()).isEmpty();
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    /** compareTo ignore l'echelle ; null-safe car certains champs (ex. plafond de tranche) valent null. */
    private static final Comparator<BigDecimal> NULL_SAFE_BIG_DECIMAL =
            Comparator.nullsFirst(Comparator.<BigDecimal>naturalOrder());

    private static <T> void assertSameContent(T actual, T expected) {
        assertThat(actual)
                .usingRecursiveComparison()
                .withComparatorForType(NULL_SAFE_BIG_DECIMAL, BigDecimal.class)
                .ignoringCollectionOrder()
                .isEqualTo(expected);
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

    // =========================================================================
    // Donnees de reference (memes valeurs que PersistenceAdaptersTest / VT-310)
    // =========================================================================

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /**
     * R-61-09-b : parametres communs (pivotDate/pivotMode/startBalance/sweepEnabled/cashCeiling/cashFloor/
     * cashAlertThreshold) de la reference Tresorerie, auparavant portes par {@code SettingsModel} (hub).
     */
    private static final TresorerieSettingsModel REFERENCE_TREASURY_SETTINGS =
            new TresorerieSettingsModel("2026-01-01", "manual", bd("5000"), null, null, null, null);

    /** R-61-09-b : parametres communs (childExitAge/taxAbattement) de la reference Fiscalite. */
    private static final TaxSettingsModel REFERENCE_TAX_SETTINGS = new TaxSettingsModel(23, bd("0.05"));

    /** R-61-09-b : parametres communs (birthYear/retireAge) de la reference Retraite. */
    private static final RetirementSettingsModel REFERENCE_RETIREMENT_SETTINGS =
            new RetirementSettingsModel(1990, 62);

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

    /**
     * R-61-09-b : amorce l'etat de reference complet directement dans chaque silo (tresorerie, fiscalite,
     * patrimoine, retraite), sans passer par {@code BudgetDataModel}/{@code PersistenceManager} (hub
     * supprime pour ce cas d'usage). Remplace l'ancien {@code referenceData()} + {@code writer.setBudgetData(...)}.
     * Loans et import bancaire ne faisaient deja pas partie de ce qu'ecrivait le hub (aucun
     * {@code LoanSnapshotWriter}/{@code BankImportSnapshotWriter} cote {@code PersistenceManager}) : les tests qui
     * en ont besoin continuent de passer par {@link #loanStore()}/{@link #bankStore()} directement.
     */
    private void seedReferenceData() {
        treasuryStore().replace(REFERENCE_TREASURY_SETTINGS,
                List.of(INCOME), List.of(CHARGE), List.of(ONEOFF), List.of(VARIABLE_INCOME),
                List.of(VARIABLE_OVERRIDE), List.of(TRANSFER));
        taxStore().replace(REFERENCE_TAX_SETTINGS, List.of(TAX_CHILD), CUSTOM_BRACKETS,
                List.of(TAX_RATE_OVERRIDE), List.of(TAX_ACTUAL_OVERRIDE));
        wealthStore().replace(List.of(PLACEMENT), List.of(REAL_ESTATE), List.of(), List.of(ASSET_CATEGORY));
        retirementStore().replace(REFERENCE_RETIREMENT_SETTINGS, RETIREMENT);
    }
}
