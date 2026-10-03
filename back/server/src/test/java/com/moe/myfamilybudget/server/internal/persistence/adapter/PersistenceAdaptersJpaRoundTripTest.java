package com.moe.myfamilybudget.server.internal.persistence.adapter;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.domain.budget.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.budget.TransferModel;
import com.moe.myfamilybudget.domain.budget.VariableIncomeModel;
import com.moe.myfamilybudget.domain.budget.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportDocumentRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowOneOffRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowTransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CreditLoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PensionPlanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthPlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthRealEstateRepository;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;

/**
 * DB-010 -- Round-trip des adaptateurs de lecture contre une vraie base (H2 en memoire, vrais repositories
 * JPA). Complete {@link PersistenceAdaptersTest} (VT-310), dont les repositories sont mockes et qui ne
 * prouve donc que la coherence du cache memoire.
 *
 * <p>Principe : l'etat est ecrit par le {@link PersistenceManager} du contexte Spring, puis relu par un
 * SECOND {@code PersistenceManager}, construit sur les memes repositories mais avec un cache vierge
 * ({@code init()} recharge alors tout depuis la base). Les adaptateurs de lecture sont branches sur ce
 * second gestionnaire : toute donnee qui n'est pas reellement stockee et relue par JPA est donc detectee.
 * Aucun redemarrage Spring n'est necessaire (voir {@code RestartPersistenceTest} / VT-320 pour cela).
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
    private PersistenceManager writer;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void startFromBlankDatabase() {
        writer.resetData();
    }

    // =========================================================================
    // Import complet
    // =========================================================================

    @Test
    @DisplayName("import complet -> chaque adaptateur relit depuis la base ce qui a ete ecrit")
    void fullImportIsReadBackFromDatabase() {
        writer.setBudgetData(referenceData());

        PersistenceManager reader = freshReader();

        assertThat(reader).isNotSameAs(writer);
        assertReferenceState(reader);
    }

    @Test
    @DisplayName("un second import remplace le premier en base (aucune ligne orpheline)")
    void secondImportReplacesFirstInDatabase() {
        writer.setBudgetData(referenceData());
        IncomeModel other = new IncomeModel("inc_2", "Freelance", bd("800"), "2027-01-01", "2030-12-31",
                bd("0"), "", "");

        writer.setBudgetData(writer.getBudgetData()
                .withIncomes(List.of(other))
                .withCharges(List.of())
                .withPlacements(List.of())
                .withObjectifs(List.of()));

        PersistenceManager reader = freshReader();
        assertSameContent(jpaBudgetAdapter(reader).getIncomes(), List.of(other));
        assertThat(jpaBudgetAdapter(reader).getCharges()).isEmpty();
        assertThat(jpaPatrimoineAdapter(reader).getPlacements()).isEmpty();
        assertThat(new GoalPersistenceAdapter(reader).getGoals()).isEmpty();
        // Les domaines non touches par le second import restent identiques.
        assertSameContent(new RetirementPersistenceAdapter(reader).getRetirement(), RETIREMENT);
        assertSameContent(new LoanPersistenceAdapter(reader).getLoans(), List.of(LOAN));
    }

    // =========================================================================
    // Mutations ciblees
    // =========================================================================

    @Test
    @DisplayName("updateRetirement -> relu en base, sans toucher aux autres domaines")
    void updateRetirementIsReadBackFromDatabase() {
        writer.setBudgetData(referenceData().withRetirement(
                new RetirementModel(List.of(), bd("47100"), bd("0.015"), bd("1.4386"), "2025-01-01", bd("0.01"))));

        writer.write(m -> m.updateRetirement(RETIREMENT));

        PersistenceManager reader = freshReader();
        assertSameContent(new RetirementPersistenceAdapter(reader).getRetirement(), RETIREMENT);
        assertSameContent(jpaBudgetAdapter(reader).getIncomes(), List.of(INCOME));
        assertSameContent(new TaxPersistenceAdapter(reader).getTaxChildren(), List.of(TAX_CHILD));
    }

    @Test
    @DisplayName("updateTaxConfig puis resetDefaultTaxBrackets -> relus en base")
    void taxConfigAndBracketResetAreReadBackFromDatabase() {
        writer.write(m -> m.updateTaxConfig(List.of(TAX_CHILD), CUSTOM_BRACKETS, List.of(TAX_RATE_OVERRIDE),
                List.of(TAX_ACTUAL_OVERRIDE)));

        TaxPersistenceAdapter afterConfig = new TaxPersistenceAdapter(freshReader());
        assertSameContent(afterConfig.getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(afterConfig.getTaxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(afterConfig.getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(afterConfig.getTaxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));

        writer.write(m -> m.resetDefaultTaxBrackets());

        TaxPersistenceAdapter afterReset = new TaxPersistenceAdapter(freshReader());
        assertDefaultBrackets(afterReset.getTaxBrackets());
        assertSameContent(afterReset.getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(afterReset.getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
    }

    @Test
    @DisplayName("addAssetCategory / removeAssetCategory -> relus en base")
    void assetCategoriesAreReadBackFromDatabase() {
        AssetCategoryModel second = new AssetCategoryModel("cat_2", "icon2", "Livrets", "epargne", "#00ff00");

        writer.write(m -> m.addAssetCategory(ASSET_CATEGORY));
        writer.write(m -> m.addAssetCategory(second));
        assertSameContent(jpaPatrimoineAdapter(freshReader()).getAssetCategories(),
                List.of(ASSET_CATEGORY, second));

        writer.write(m -> m.removeAssetCategory(ASSET_CATEGORY.id()));
        assertSameContent(jpaPatrimoineAdapter(freshReader()).getAssetCategories(), List.of(second));
    }

    @Test
    @DisplayName("updateBankImport -> transactions, categories et pointages relus en base")
    void bankImportIsReadBackFromDatabase() {
        writer.write(m -> m.updateBankImport(BANK_IMPORT));

        BankImportModel bank = new BankPersistenceAdapter(freshReader()).getBankImport();

        assertSameContent(bank.categories(), BANK_IMPORT.categories());
        assertSameContent(bank.transactions(), BANK_IMPORT.transactions());
        assertSameContent(bank.matchings(), BANK_IMPORT.matchings());
    }

    // =========================================================================
    // Reset
    // =========================================================================

    @Test
    @DisplayName("resetData -> tous les adaptateurs relisent l'etat par defaut depuis la base")
    void resetIsReadBackFromDatabase() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        PersistenceManager reader = freshReader();
        assertThat(jpaBudgetAdapter(reader).getIncomes()).isEmpty();
        assertThat(jpaBudgetAdapter(reader).getCharges()).isEmpty();
        assertThat(jpaBudgetAdapter(reader).getOneoffExpenses()).isEmpty();
        assertThat(jpaBudgetAdapter(reader).getVariableIncomes()).isEmpty();
        assertThat(jpaBudgetAdapter(reader).getVariableOverrides()).isEmpty();
        assertThat(jpaPatrimoineAdapter(reader).getPlacements()).isEmpty();
        assertThat(jpaPatrimoineAdapter(reader).getRealEstate()).isEmpty();
        assertThat(jpaPatrimoineAdapter(reader).getAssetCategories()).isEmpty();
        assertThat(jpaPatrimoineAdapter(reader).getTransfers()).isEmpty();
        assertThat(new RetirementPersistenceAdapter(reader).getRetirement().people()).isEmpty();
        assertThat(new TaxPersistenceAdapter(reader).getTaxChildren()).isEmpty();
        assertThat(new TaxPersistenceAdapter(reader).getTaxRateOverrides()).isEmpty();
        assertThat(new TaxPersistenceAdapter(reader).getTaxActualOverrides()).isEmpty();
        assertDefaultBrackets(new TaxPersistenceAdapter(reader).getTaxBrackets());
        assertThat(new BankPersistenceAdapter(reader).getBankImport().transactions()).isEmpty();
        assertThat(new LoanPersistenceAdapter(reader).getLoans()).isEmpty();
        assertThat(new GoalPersistenceAdapter(reader).getGoals()).isEmpty();
    }

    @Test
    @DisplayName("reset puis nouvel import -> l'etat de reference est relu en base")
    void importAfterResetIsReadBackFromDatabase() {
        writer.setBudgetData(referenceData());
        writer.resetData();

        writer.setBudgetData(referenceData());

        assertReferenceState(freshReader());
    }

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

        writer.setBudgetData(writer.getBudgetData()
                .withPlacements(List.of(bare))
                .withObjectifs(List.of(bareGoal))
                .withRetirement(noPeople));

        PersistenceManager reader = freshReader();
        List<PlacementModel> placements = jpaPatrimoineAdapter(reader).getPlacements();
        assertThat(placements).hasSize(1);
        assertThat(placements.get(0).id()).isEqualTo("plc_bare");
        assertThat(placements.get(0).history()).isNotNull().isEmpty();
        List<ObjectifModel> goals = new GoalPersistenceAdapter(reader).getGoals();
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).id()).isEqualTo("goal_bare");
        assertThat(goals.get(0).allocations()).isNotNull().isEmpty();
        assertThat(new RetirementPersistenceAdapter(reader).getRetirement().people()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("import sans bareme fiscal -> le bareme par defaut est relu")
    void emptyBracketsAreReadBackAsDefaultSchedule() {
        writer.setBudgetData(referenceData().withTaxBrackets(List.of()));

        assertDefaultBrackets(new TaxPersistenceAdapter(freshReader()).getTaxBrackets());
    }

    // =========================================================================
    // DB-1021 -- Objectifs lus depuis les tables autonomes
    // =========================================================================

    private GoalPersistenceAdapter jpaGoalAdapter(PersistenceManager manager) {
        return new GoalPersistenceAdapter(manager, context.getBean(GoalRepository.class));
    }

    @Test
    @DisplayName("DB-1021 -- import -> les objectifs sont recopies dans les tables autonomes et relus par JPA")
    void importedGoalsAreReadFromGoalTables() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(GoalRepository.class).count()).isEqualTo(1);
        List<ObjectifModel> goals = jpaGoalAdapter(freshReader()).getGoals();
        assertSameContent(goals, List.of(GOAL));
        assertThat(goals.get(0).allocations()).hasSize(1);
    }

    @Test
    @DisplayName("DB-1021 -- creation, mise a jour et suppression d'un objectif sont visibles via la lecture JPA")
    void goalWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        GoalPersistenceAdapter adapter = jpaGoalAdapter(writer);

        adapter.saveGoalRow(Map.of("id", "goal_2", "label", "Voiture", "targetAmount", bd("8000"),
                "targetDate", "2028-01-01"));
        assertThat(adapter.getGoals()).extracting(ObjectifModel::id).containsExactlyInAnyOrder("goal_1", "goal_2");

        adapter.saveGoalRow(Map.of("id", "goal_2", "label", "Voiture neuve", "targetAmount", bd("9000"),
                "targetDate", "2028-01-01"));
        assertThat(adapter.getGoals()).filteredOn(g -> "goal_2".equals(g.id()))
                .singleElement()
                .satisfies(g -> assertThat(g.label()).isEqualTo("Voiture neuve"));

        adapter.deleteGoalRow("goal_1");
        assertThat(jpaGoalAdapter(freshReader()).getGoals()).extracting(ObjectifModel::id)
                .containsExactly("goal_2");
    }

    @Test
    @DisplayName("DB-1120 -- au demarrage, les objectifs du cache sont recharges depuis les tables goal_*")
    void goalsAreReloadedFromGoalTablesOnStartup() {
        writer.setBudgetData(referenceData());

        PersistenceManager restarted = freshReader();

        assertSameContent(restarted.getBudgetData().objectifs(), List.of(GOAL));
        assertSameContent(jpaGoalAdapter(restarted).getGoals(), List.of(GOAL));
    }

    @Test
    @DisplayName("DB-1021 -- reinitialisation -> les tables autonomes sont videes")
    void resetEmptiesGoalTables() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        assertThat(context.getBean(GoalRepository.class).count()).isZero();
        assertThat(jpaGoalAdapter(writer).getGoals()).isEmpty();
    }

    // =========================================================================
    // DB-1041 -- Prets lus depuis la table autonome
    // =========================================================================

    private LoanPersistenceAdapter jpaLoanAdapter(PersistenceManager manager) {
        return new LoanPersistenceAdapter(manager, context.getBean(CreditLoanRepository.class));
    }

    @Test
    @DisplayName("DB-1041 -- import -> les prets sont recopies dans la table autonome et relus par JPA")
    void importedLoansAreReadFromCreditTable() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(CreditLoanRepository.class).count()).isEqualTo(1);
        assertSameContent(jpaLoanAdapter(freshReader()).getLoans(), List.of(LOAN));
    }

    @Test
    @DisplayName("DB-1041 -- creation, mise a jour et suppression d'un pret sont visibles via la lecture JPA")
    void loanWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        LoanPersistenceAdapter adapter = jpaLoanAdapter(writer);

        adapter.saveLoanRow(Map.of("id", "loan_2", "label", "Pret auto", "crd", bd("12000"),
                "rate", bd("0.02512"), "monthly", bd("300")));
        assertThat(adapter.getLoans()).extracting(LoanModel::id).containsExactlyInAnyOrder("loan_1", "loan_2");
        assertThat(adapter.getLoans()).filteredOn(l -> "loan_2".equals(l.id()))
                .singleElement()
                .satisfies(l -> assertThat(l.rate()).isEqualByComparingTo("0.02512"));

        adapter.saveLoanRow(Map.of("id", "loan_2", "label", "Pret auto solde", "crd", bd("0"),
                "rate", bd("0.02512"), "monthly", bd("300")));
        assertThat(adapter.getLoans()).filteredOn(l -> "loan_2".equals(l.id()))
                .singleElement()
                .satisfies(l -> assertThat(l.label()).isEqualTo("Pret auto solde"));

        adapter.deleteLoanRow("loan_1");
        assertThat(jpaLoanAdapter(freshReader()).getLoans()).extracting(LoanModel::id)
                .containsExactly("loan_2");
    }

    @Test
    @DisplayName("DB-1041 -- table autonome vide au demarrage (donnees pre-existantes) -> reconstruite depuis le hub")
    void creditTableIsRebuiltFromHubOnStartup() {
        writer.setBudgetData(referenceData());
        context.getBean(CreditLoanRepository.class).deleteAll();
        assertThat(context.getBean(CreditLoanRepository.class).count()).isZero();

        PersistenceManager restarted = freshReader();

        assertSameContent(jpaLoanAdapter(restarted).getLoans(), List.of(LOAN));
    }

    @Test
    @DisplayName("DB-1041 -- reinitialisation -> la table autonome est videe")
    void resetEmptiesCreditTable() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        assertThat(context.getBean(CreditLoanRepository.class).count()).isZero();
        assertThat(jpaLoanAdapter(writer).getLoans()).isEmpty();
    }

    // =========================================================================
    // DB-1011 -- Fiscalite lue depuis les tables autonomes
    // =========================================================================

    private TaxPersistenceAdapter jpaTaxAdapter(PersistenceManager manager) {
        return new TaxPersistenceAdapter(manager,
                context.getBean(FiscalChildRepository.class),
                context.getBean(FiscalBracketRepository.class),
                context.getBean(FiscalRateOverrideRepository.class),
                context.getBean(FiscalActualOverrideRepository.class));
    }

    @Test
    @DisplayName("DB-1011 -- import -> la fiscalite est recopiee dans les tables autonomes et relue par JPA")
    void importedTaxConfigIsReadFromFiscalTables() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(FiscalChildRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(FiscalBracketRepository.class).count()).isEqualTo(CUSTOM_BRACKETS.size());
        TaxPersistenceAdapter adapter = jpaTaxAdapter(freshReader());
        assertSameContent(adapter.getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(adapter.getTaxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(adapter.getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(adapter.getTaxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));
    }

    @Test
    @DisplayName("DB-1011 -- updateTaxConfig et resetDefaultTaxBrackets sont visibles via la lecture JPA")
    void taxWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        TaxPersistenceAdapter adapter = jpaTaxAdapter(writer);
        TaxChildModel secondChild = new TaxChildModel("tc_2", "Lucas", 2018);

        adapter.updateTaxConfig(List.of(TAX_CHILD, secondChild), CUSTOM_BRACKETS, List.of(), List.of());

        assertThat(adapter.getTaxChildren()).extracting(TaxChildModel::id).containsExactly("tc_1", "tc_2");
        assertThat(adapter.getTaxRateOverrides()).isEmpty();
        assertThat(adapter.getTaxActualOverrides()).isEmpty();
        assertSameContent(adapter.getTaxBrackets(), CUSTOM_BRACKETS);

        adapter.resetDefaultTaxBrackets();

        TaxPersistenceAdapter reread = jpaTaxAdapter(freshReader());
        assertDefaultBrackets(reread.getTaxBrackets());
        assertThat(reread.getTaxChildren()).extracting(TaxChildModel::id).containsExactly("tc_1", "tc_2");
    }

    @Test
    @DisplayName("DB-1011 -- import sans bareme -> le bareme par defaut est ecrit puis relu par JPA")
    void emptyBracketsAreReadAsDefaultScheduleThroughJpa() {
        writer.setBudgetData(referenceData().withTaxBrackets(List.of()));

        assertDefaultBrackets(jpaTaxAdapter(freshReader()).getTaxBrackets());
    }

    @Test
    @DisplayName("DB-1110 -- au demarrage, la fiscalite du cache est rechargee depuis les tables fiscal_*")
    void fiscalDataIsReloadedFromFiscalTablesOnStartup() {
        writer.setBudgetData(referenceData());

        PersistenceManager reloaded = freshReader();
        assertSameContent(reloaded.getBudgetData().taxChildren(), List.of(TAX_CHILD));
        assertSameContent(reloaded.getBudgetData().taxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(reloaded.getBudgetData().taxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(reloaded.getBudgetData().taxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));

        TaxPersistenceAdapter restarted = jpaTaxAdapter(reloaded);

        assertSameContent(restarted.getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(restarted.getTaxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(restarted.getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(restarted.getTaxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));
    }

    @Test
    @DisplayName("DB-1011 -- reinitialisation -> listes videes et bareme par defaut relu par JPA")
    void resetClearsFiscalTablesAndRestoresDefaultBrackets() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        TaxPersistenceAdapter adapter = jpaTaxAdapter(writer);
        assertThat(adapter.getTaxChildren()).isEmpty();
        assertThat(adapter.getTaxRateOverrides()).isEmpty();
        assertThat(adapter.getTaxActualOverrides()).isEmpty();
        assertDefaultBrackets(adapter.getTaxBrackets());
    }

    // =========================================================================
    // DB-1001 -- Retraite lue depuis les tables autonomes
    // =========================================================================

    private RetirementPersistenceAdapter jpaRetirementAdapter(PersistenceManager manager) {
        return new RetirementPersistenceAdapter(manager, context.getBean(PensionPlanRepository.class));
    }

    @Test
    @DisplayName("DB-1001 -- import -> la retraite est recopiee dans les tables autonomes et relue par JPA")
    void importedRetirementIsReadFromPensionTables() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(PensionPlanRepository.class).count()).isEqualTo(1);
        RetirementModel retirement = jpaRetirementAdapter(freshReader()).getRetirement();
        assertSameContent(retirement, RETIREMENT);
        assertThat(retirement.people().get(0).salaryHistory()).extracting(h -> h.year())
                .containsExactly(2023, 2024, 2025);
    }

    @Test
    @DisplayName("DB-1001 -- updateRetirement est visible via la lecture JPA (un seul plan, ancien contenu remplace)")
    void retirementWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        RetirementPersistenceAdapter adapter = jpaRetirementAdapter(writer);
        RetirementModel updated = new RetirementModel(
                List.of(new RetirementModel.RetirementPersonModel("p_2", "Bob", 1988, "Salaire", 120, "2025-12-31",
                        List.of(new RetirementModel.SalaryHistoryModel(2025, bd("41000"))),
                        bd("1800"), bd("0.00512345"), null)),
                bd("47100"), bd("0.015"), bd("1.4386"), "2025-01-01", bd("0.01"));

        adapter.updateRetirement(updated);

        assertThat(context.getBean(PensionPlanRepository.class).count()).isEqualTo(1);
        assertSameContent(adapter.getRetirement(), updated);
        RetirementModel reread = jpaRetirementAdapter(freshReader()).getRetirement();
        assertSameContent(reread, updated);
        assertThat(reread.people().get(0).ratioPointsParEuro()).isEqualByComparingTo("0.00512345");
    }

    @Test
    @DisplayName("DB-1100 -- au demarrage, la retraite du cache est rechargee depuis les tables pension_*")
    void retirementIsReloadedFromPensionTablesOnStartup() {
        writer.setBudgetData(referenceData());

        PersistenceManager restarted = freshReader();

        assertSameContent(restarted.getBudgetData().retirement(), RETIREMENT);
        assertSameContent(jpaRetirementAdapter(restarted).getRetirement(), RETIREMENT);
    }

    @Test
    @DisplayName("DB-1001 -- reinitialisation -> la retraite par defaut (sans personne) est relue par JPA")
    void resetRestoresEmptyRetirementThroughJpa() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        assertThat(context.getBean(PensionPlanRepository.class).count()).isLessThanOrEqualTo(1);
        assertThat(jpaRetirementAdapter(writer).getRetirement().people()).isEmpty();
    }

    // =========================================================================
    // DB-1031 -- Import bancaire lu depuis la table autonome
    // =========================================================================

    private BankPersistenceAdapter jpaBankAdapter(PersistenceManager manager) {
        return new BankPersistenceAdapter(manager, context.getBean(BankImportDocumentRepository.class));
    }

    @Test
    @DisplayName("DB-1031 -- import -> l'import bancaire est recopie dans la table autonome et relu par JPA")
    void importedBankImportIsReadFromDocumentTable() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(BankImportDocumentRepository.class).count()).isEqualTo(1);
        BankImportModel bank = jpaBankAdapter(freshReader()).getBankImport();
        assertSameContent(bank.categories(), BANK_IMPORT.categories());
        assertSameContent(bank.transactions(), BANK_IMPORT.transactions());
        assertSameContent(bank.matchings(), BANK_IMPORT.matchings());
    }

    @Test
    @DisplayName("DB-1031 -- updateBankImport remplace le document (une seule ligne) et est visible via la lecture JPA")
    void bankWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        BankPersistenceAdapter adapter = jpaBankAdapter(writer);
        BankImportModel updated = new BankImportModel(
                null,
                BANK_IMPORT.categories(),
                List.of(),
                List.of(new BankImportModel.BankTransactionModel("tx_2", "2026-06-01", "Cafe \u00e9t\u00e9", "CB",
                        bd("-3.5"), "cat_loyer", List.of())),
                List.of(),
                List.of());

        adapter.updateBankImport(updated);

        assertThat(context.getBean(BankImportDocumentRepository.class).count()).isEqualTo(1);
        assertThat(adapter.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_2");
        BankImportModel reread = jpaBankAdapter(freshReader()).getBankImport();
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
        writer.setBudgetData(referenceData().withBankImport(new BankImportModel(
                null, BANK_IMPORT.categories(), List.of(), many, List.of(), List.of())));

        List<BankImportModel.BankTransactionModel> reread =
                jpaBankAdapter(freshReader()).getBankImport().transactions();
        assertThat(reread).hasSize(3000);
        assertThat(reread.get(2999).id()).isEqualTo("tx_2999");
    }

    @Test
    @DisplayName("DB-1130 -- au demarrage, l'import bancaire du cache est recharge depuis bank_import_document")
    void bankImportIsReloadedFromDocumentTableOnStartup() {
        writer.setBudgetData(referenceData());

        PersistenceManager restarted = freshReader();

        assertSameContent(restarted.getBudgetData().bankImport().transactions(), BANK_IMPORT.transactions());
        assertSameContent(jpaBankAdapter(restarted).getBankImport().transactions(), BANK_IMPORT.transactions());
    }

    @Test
    @DisplayName("DB-1031 -- reinitialisation -> un import vide (jamais null) est relu par JPA")
    void resetRestoresEmptyBankImportThroughJpa() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        BankImportModel bank = jpaBankAdapter(writer).getBankImport();
        assertThat(bank).isNotNull();
        assertThat(bank.transactions()).isEmpty();
        assertThat(bank.categories()).isEmpty();
    }

    @Test
    @DisplayName("DB-1031 -- table autonome sans document -> un import vide est restitue")
    void missingDocumentIsReadAsEmptyImport() {
        writer.setBudgetData(referenceData());
        context.getBean(BankImportDocumentRepository.class).deleteAll();

        BankImportModel bank = jpaBankAdapter(writer).getBankImport();

        assertThat(bank).isNotNull();
        assertThat(bank.transactions()).isEmpty();
    }

    // =========================================================================
    // DB-1051 -- Patrimoine lu depuis les tables autonomes
    // =========================================================================

    private PatrimoinePersistenceAdapter jpaPatrimoineAdapter(PersistenceManager manager) {
        return new PatrimoinePersistenceAdapter(manager,
                context.getBean(WealthPlacementRepository.class),
                context.getBean(WealthRealEstateRepository.class),
                context.getBean(WealthCategoryRepository.class),
                context.getBean(CashflowTransferRepository.class));
    }

    @Test
    @DisplayName("DB-1051 -- import -> placements, immobilier et categories recopies dans les tables autonomes et relus par JPA")
    void importedWealthIsReadFromWealthTables() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(WealthPlacementRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(WealthRealEstateRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(WealthCategoryRepository.class).count()).isEqualTo(1);

        PatrimoinePersistenceAdapter adapter = jpaPatrimoineAdapter(freshReader());
        assertSameContent(adapter.getPlacements(), List.of(PLACEMENT));
        assertThat(adapter.getPlacements().get(0).history()).hasSize(1);
        assertSameContent(adapter.getRealEstate(), List.of(REAL_ESTATE));
        assertSameContent(adapter.getAssetCategories(), List.of(ASSET_CATEGORY));
    }

    @Test
    @DisplayName("DB-1051 -- creation, mise a jour et suppression d'un placement sont visibles via la lecture JPA")
    void placementWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        PatrimoinePersistenceAdapter adapter = jpaPatrimoineAdapter(writer);

        adapter.savePatrimoineRow(PatrimoineList.PLACEMENTS, Map.of("id", "plc_2", "label", "Livret A",
                "category", "Epargne", "balance", bd("5000"), "ratePess", bd("0.02512")));
        assertThat(adapter.getPlacements()).extracting(PlacementModel::id)
                .containsExactlyInAnyOrder("plc_1", "plc_2");
        assertThat(adapter.getPlacements()).filteredOn(p -> "plc_2".equals(p.id()))
                .singleElement()
                .satisfies(p -> assertThat(p.ratePess()).isEqualByComparingTo("0.02512"));

        adapter.savePatrimoineRow(PatrimoineList.PLACEMENTS, Map.of("id", "plc_2", "label", "Livret A renomme",
                "category", "Epargne", "balance", bd("5200")));
        assertThat(adapter.getPlacements()).filteredOn(p -> "plc_2".equals(p.id()))
                .singleElement()
                .satisfies(p -> assertThat(p.label()).isEqualTo("Livret A renomme"));

        adapter.deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");
        assertThat(jpaPatrimoineAdapter(freshReader()).getPlacements()).extracting(PlacementModel::id)
                .containsExactly("plc_2");
    }

    @Test
    @DisplayName("DB-1051 -- l'historique d'un placement (ajout, mise a jour, suppression) est visible via la lecture JPA")
    void placementHistoryWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        PatrimoinePersistenceAdapter adapter = jpaPatrimoineAdapter(writer);

        Map<String, Object> added = adapter.addPlacementHistoryEntry("plc_1",
                Map.of("date", "2026-06-01", "value", bd("10400"), "notes", "releve juin"));
        String entryId = String.valueOf(added.get("id"));
        assertThat(adapter.getPlacements().get(0).history()).extracting(PlacementHistoryEntryModel::id)
                .containsExactlyInAnyOrder("h_1", entryId);

        adapter.updatePlacementHistoryEntry("plc_1", entryId, Map.of("value", bd("10450")));
        assertThat(jpaPatrimoineAdapter(freshReader()).getPlacements().get(0).history())
                .filteredOn(h -> entryId.equals(h.id()))
                .singleElement()
                .satisfies(h -> assertThat(h.value()).isEqualByComparingTo("10450"));

        adapter.deletePlacementHistoryEntry("plc_1", "h_1");
        assertThat(jpaPatrimoineAdapter(freshReader()).getPlacements().get(0).history())
                .extracting(PlacementHistoryEntryModel::id).containsExactly(entryId);
    }

    @Test
    @DisplayName("DB-1051 -- biens immobiliers et categories d'actifs : ecritures visibles via la lecture JPA")
    void realEstateAndCategoryWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());
        PatrimoinePersistenceAdapter adapter = jpaPatrimoineAdapter(writer);

        adapter.savePatrimoineRow(PatrimoineList.REAL_ESTATE, Map.of("id", "re_2", "label", "Studio",
                "currentValue", bd("90000")));
        assertThat(adapter.getRealEstate()).extracting(RealEstateModel::id)
                .containsExactlyInAnyOrder("re_1", "re_2");
        adapter.deletePatrimoineRow(PatrimoineList.REAL_ESTATE, "re_1");
        assertThat(jpaPatrimoineAdapter(freshReader()).getRealEstate()).extracting(RealEstateModel::id)
                .containsExactly("re_2");

        AssetCategoryModel second = new AssetCategoryModel("cat_2", "icon2", "Livrets", "epargne", "#00ff00");
        adapter.addAssetCategory(second);
        assertSameContent(jpaPatrimoineAdapter(freshReader()).getAssetCategories(), List.of(ASSET_CATEGORY, second));
        adapter.removeAssetCategory("cat_1");
        assertSameContent(jpaPatrimoineAdapter(freshReader()).getAssetCategories(), List.of(second));
    }

    @Test
    @DisplayName("DB-1051 -- tables autonomes vides au demarrage (donnees pre-existantes) -> reconstruites depuis le hub")
    void wealthTablesAreRebuiltFromHubOnStartup() {
        writer.setBudgetData(referenceData());
        context.getBean(WealthPlacementRepository.class).deleteAll();
        context.getBean(WealthRealEstateRepository.class).deleteAll();
        context.getBean(WealthCategoryRepository.class).deleteAll();
        assertThat(context.getBean(WealthPlacementRepository.class).count()).isZero();
        assertThat(context.getBean(WealthRealEstateRepository.class).count()).isZero();
        assertThat(context.getBean(WealthCategoryRepository.class).count()).isZero();

        PersistenceManager restarted = freshReader();

        PatrimoinePersistenceAdapter adapter = jpaPatrimoineAdapter(restarted);
        assertSameContent(adapter.getPlacements(), List.of(PLACEMENT));
        assertSameContent(adapter.getRealEstate(), List.of(REAL_ESTATE));
        assertSameContent(adapter.getAssetCategories(), List.of(ASSET_CATEGORY));
    }

    @Test
    @DisplayName("DB-1051 -- reinitialisation -> les tables autonomes sont videes")
    void resetEmptiesWealthTables() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        assertThat(context.getBean(WealthPlacementRepository.class).count()).isZero();
        assertThat(context.getBean(WealthRealEstateRepository.class).count()).isZero();
        assertThat(context.getBean(WealthCategoryRepository.class).count()).isZero();
        PatrimoinePersistenceAdapter adapter = jpaPatrimoineAdapter(writer);
        assertThat(adapter.getPlacements()).isEmpty();
        assertThat(adapter.getRealEstate()).isEmpty();
        assertThat(adapter.getAssetCategories()).isEmpty();
    }

    // =========================================================================
    // DB-1061 -- Tresorerie lue depuis les tables autonomes
    // =========================================================================

    private BudgetPersistenceAdapter jpaBudgetAdapter(PersistenceManager manager) {
        return new BudgetPersistenceAdapter(manager,
                context.getBean(CashflowIncomeRepository.class),
                context.getBean(CashflowChargeRepository.class),
                context.getBean(CashflowOneOffRepository.class),
                context.getBean(CashflowVariableIncomeRepository.class),
                context.getBean(CashflowVariableOverrideRepository.class));
    }

    @Test
    @DisplayName("DB-1061 -- import -> les six listes de tresorerie sont recopiees dans les tables autonomes et relues par JPA")
    void importedCashflowIsReadFromCashflowTables() {
        writer.setBudgetData(referenceData());

        assertThat(context.getBean(CashflowIncomeRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowChargeRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowOneOffRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowTransferRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowVariableIncomeRepository.class).count()).isEqualTo(1);
        assertThat(context.getBean(CashflowVariableOverrideRepository.class).count()).isEqualTo(1);

        PersistenceManager reader = freshReader();
        BudgetPersistenceAdapter budget = jpaBudgetAdapter(reader);
        assertSameContent(budget.getIncomes(), List.of(INCOME));
        assertSameContent(budget.getCharges(), List.of(CHARGE));
        assertSameContent(budget.getOneoffExpenses(), List.of(ONEOFF));
        assertSameContent(budget.getVariableIncomes(), List.of(VARIABLE_INCOME));
        assertSameContent(budget.getVariableOverrides(), List.of(VARIABLE_OVERRIDE));
        assertSameContent(jpaPatrimoineAdapter(reader).getTransfers(), List.of(TRANSFER));
    }

    @Test
    @DisplayName("DB-1061 -- ajout, mise a jour et suppression d'une ligne de tresorerie sont visibles via la lecture JPA")
    void cashflowWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());

        writer.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_2", "label", "Freelance", "monthly", bd("800"))));
        assertThat(jpaBudgetAdapter(freshReader()).getIncomes()).extracting(IncomeModel::id)
                .containsExactlyInAnyOrder("inc_1", "inc_2");

        writer.write(m -> m.updateTresorerieRow("incomes", "inc_2", "monthly", bd("950")));
        assertThat(jpaBudgetAdapter(freshReader()).getIncomes()).filteredOn(i -> "inc_2".equals(i.id()))
                .singleElement()
                .satisfies(i -> assertThat(i.monthly()).isEqualByComparingTo("950"));

        writer.write(m -> m.removeTresorerieRow("incomes", "inc_1"));
        assertThat(jpaBudgetAdapter(freshReader()).getIncomes()).extracting(IncomeModel::id)
                .containsExactly("inc_2");
    }

    @Test
    @DisplayName("DB-1061 -- virements : ecritures visibles via la lecture JPA du Patrimoine")
    void transferWritesAreVisibleThroughJpaReader() {
        writer.setBudgetData(referenceData());

        writer.write(m -> m.savePatrimoineRow("transfers", Map.of("id", "tr_2", "amount", bd("250"))));
        assertThat(jpaPatrimoineAdapter(freshReader()).getTransfers()).extracting(TransferModel::id)
                .containsExactlyInAnyOrder("tr_1", "tr_2");

        writer.write(m -> m.deletePatrimoineRow("transfers", "tr_1"));
        assertThat(jpaPatrimoineAdapter(freshReader()).getTransfers()).extracting(TransferModel::id)
                .containsExactly("tr_2");
    }

    @Test
    @DisplayName("DB-1061 -- tables autonomes vides au demarrage (donnees pre-existantes) -> reconstruites depuis le hub")
    void cashflowTablesAreRebuiltFromHubOnStartup() {
        writer.setBudgetData(referenceData());
        context.getBean(CashflowIncomeRepository.class).deleteAll();
        context.getBean(CashflowChargeRepository.class).deleteAll();
        context.getBean(CashflowOneOffRepository.class).deleteAll();
        context.getBean(CashflowTransferRepository.class).deleteAll();
        context.getBean(CashflowVariableIncomeRepository.class).deleteAll();
        context.getBean(CashflowVariableOverrideRepository.class).deleteAll();
        assertThat(context.getBean(CashflowIncomeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowTransferRepository.class).count()).isZero();

        PersistenceManager restarted = freshReader();

        BudgetPersistenceAdapter budget = jpaBudgetAdapter(restarted);
        assertSameContent(budget.getIncomes(), List.of(INCOME));
        assertSameContent(budget.getCharges(), List.of(CHARGE));
        assertSameContent(budget.getOneoffExpenses(), List.of(ONEOFF));
        assertSameContent(budget.getVariableIncomes(), List.of(VARIABLE_INCOME));
        assertSameContent(budget.getVariableOverrides(), List.of(VARIABLE_OVERRIDE));
        assertSameContent(jpaPatrimoineAdapter(restarted).getTransfers(), List.of(TRANSFER));
    }

    @Test
    @DisplayName("DB-1061 -- reinitialisation -> les tables autonomes sont videes")
    void resetEmptiesCashflowTables() {
        writer.setBudgetData(referenceData());

        writer.resetData();

        assertThat(context.getBean(CashflowIncomeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowChargeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowOneOffRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowTransferRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowVariableIncomeRepository.class).count()).isZero();
        assertThat(context.getBean(CashflowVariableOverrideRepository.class).count()).isZero();
        BudgetPersistenceAdapter budget = jpaBudgetAdapter(writer);
        assertThat(budget.getIncomes()).isEmpty();
        assertThat(budget.getCharges()).isEmpty();
        assertThat(jpaPatrimoineAdapter(writer).getTransfers()).isEmpty();
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    /**
     * Construit un second {@link PersistenceManager} sur les memes repositories : son cache est vierge et
     * {@code init()} relit donc tout depuis la base.
     */
    private PersistenceManager freshReader() {
        PersistenceManager reader = new PersistenceManager(
                context.getBean(BudgetDataRepository.class),
                context.getBean(SettingsRepository.class),
                context.getBean(IncomeRepository.class),
                context.getBean(ChargeRepository.class),
                context.getBean(PlacementRepository.class),
                context.getBean(RealEstateRepository.class),
                context.getBean(OneOffExpenseRepository.class),
                context.getBean(TransferRepository.class),
                context.getBean(VariableIncomeRepository.class),
                context.getBean(VariableOverrideRepository.class),
                context.getBean(AssetCategoryRepository.class),
                context.getBean(LoanRepository.class),
                context.getBean(GoalRepository.class),
                context.getBean(CreditLoanRepository.class),
                context.getBean(FiscalChildRepository.class),
                context.getBean(FiscalBracketRepository.class),
                context.getBean(FiscalRateOverrideRepository.class),
                context.getBean(FiscalActualOverrideRepository.class),
                context.getBean(PensionPlanRepository.class),
                context.getBean(BankImportDocumentRepository.class),
                context.getBean(WealthPlacementRepository.class),
                context.getBean(WealthRealEstateRepository.class),
                context.getBean(WealthCategoryRepository.class),
                context.getBean(CashflowIncomeRepository.class),
                context.getBean(CashflowChargeRepository.class),
                context.getBean(CashflowOneOffRepository.class),
                context.getBean(CashflowTransferRepository.class),
                context.getBean(CashflowVariableIncomeRepository.class),
                context.getBean(CashflowVariableOverrideRepository.class),
                transactionManager,
                eventPublisher);
        reader.init();
        return reader;
    }

    private void assertReferenceState(PersistenceManager reader) {
        BudgetPersistenceAdapter budget = jpaBudgetAdapter(reader);
        assertSameContent(budget.getIncomes(), List.of(INCOME));
        assertSameContent(budget.getCharges(), List.of(CHARGE));
        assertSameContent(budget.getOneoffExpenses(), List.of(ONEOFF));
        assertSameContent(budget.getVariableIncomes(), List.of(VARIABLE_INCOME));
        assertSameContent(budget.getVariableOverrides(), List.of(VARIABLE_OVERRIDE));

        PatrimoinePersistenceAdapter patrimoine = jpaPatrimoineAdapter(reader);
        assertSameContent(patrimoine.getPlacements(), List.of(PLACEMENT));
        assertThat(patrimoine.getPlacements().get(0).history()).hasSize(1);
        assertSameContent(patrimoine.getRealEstate(), List.of(REAL_ESTATE));
        assertSameContent(patrimoine.getAssetCategories(), List.of(ASSET_CATEGORY));
        assertSameContent(patrimoine.getTransfers(), List.of(TRANSFER));

        RetirementModel retirement = new RetirementPersistenceAdapter(reader).getRetirement();
        assertSameContent(retirement, RETIREMENT);
        assertThat(retirement.people().get(0).salaryHistory()).hasSize(3);

        TaxPersistenceAdapter tax = new TaxPersistenceAdapter(reader);
        assertSameContent(tax.getTaxChildren(), List.of(TAX_CHILD));
        assertSameContent(tax.getTaxBrackets(), CUSTOM_BRACKETS);
        assertSameContent(tax.getTaxRateOverrides(), List.of(TAX_RATE_OVERRIDE));
        assertSameContent(tax.getTaxActualOverrides(), List.of(TAX_ACTUAL_OVERRIDE));

        BankImportModel bank = new BankPersistenceAdapter(reader).getBankImport();
        assertSameContent(bank.categories(), BANK_IMPORT.categories());
        assertSameContent(bank.transactions(), BANK_IMPORT.transactions());
        assertSameContent(bank.matchings(), BANK_IMPORT.matchings());

        assertSameContent(new LoanPersistenceAdapter(reader).getLoans(), List.of(LOAN));
        List<ObjectifModel> goals = new GoalPersistenceAdapter(reader).getGoals();
        assertSameContent(goals, List.of(GOAL));
        assertThat(goals.get(0).allocations()).hasSize(1);

        SettingsModel settings = new SettingsPersistenceAdapter(reader).getSettings();
        assertThat(settings.birthYear()).isEqualTo(SETTINGS.birthYear());
        assertThat(settings.retireAge()).isEqualTo(SETTINGS.retireAge());
        assertThat(settings.simulateUntilAge()).isEqualTo(SETTINGS.simulateUntilAge());
        assertThat(settings.inflationRate()).isEqualByComparingTo(SETTINGS.inflationRate());
        assertThat(settings.pivotDate()).isEqualTo(SETTINGS.pivotDate());
        assertThat(settings.pivotMode()).isEqualTo(SETTINGS.pivotMode());
        assertThat(settings.startBalance()).isEqualByComparingTo(SETTINGS.startBalance());
        assertThat(settings.childExitAge()).isEqualTo(SETTINGS.childExitAge());
        assertThat(settings.taxAbattement()).isEqualByComparingTo(SETTINGS.taxAbattement());
    }

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
        return writer.getBudgetData()
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
