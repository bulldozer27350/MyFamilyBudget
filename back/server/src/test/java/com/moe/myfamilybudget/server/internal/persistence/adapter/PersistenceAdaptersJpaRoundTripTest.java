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
import com.moe.myfamilybudget.server.internal.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CreditLoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ObjectifRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RetirementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableOverrideRepository;

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
        assertSameContent(new BudgetPersistenceAdapter(reader).getIncomes(), List.of(other));
        assertThat(new BudgetPersistenceAdapter(reader).getCharges()).isEmpty();
        assertThat(new PatrimoinePersistenceAdapter(reader).getPlacements()).isEmpty();
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
        assertSameContent(new BudgetPersistenceAdapter(reader).getIncomes(), List.of(INCOME));
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
        assertSameContent(new PatrimoinePersistenceAdapter(freshReader()).getAssetCategories(),
                List.of(ASSET_CATEGORY, second));

        writer.write(m -> m.removeAssetCategory(ASSET_CATEGORY.id()));
        assertSameContent(new PatrimoinePersistenceAdapter(freshReader()).getAssetCategories(), List.of(second));
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
        assertThat(new BudgetPersistenceAdapter(reader).getIncomes()).isEmpty();
        assertThat(new BudgetPersistenceAdapter(reader).getCharges()).isEmpty();
        assertThat(new BudgetPersistenceAdapter(reader).getOneoffExpenses()).isEmpty();
        assertThat(new BudgetPersistenceAdapter(reader).getVariableIncomes()).isEmpty();
        assertThat(new BudgetPersistenceAdapter(reader).getVariableOverrides()).isEmpty();
        assertThat(new PatrimoinePersistenceAdapter(reader).getPlacements()).isEmpty();
        assertThat(new PatrimoinePersistenceAdapter(reader).getRealEstate()).isEmpty();
        assertThat(new PatrimoinePersistenceAdapter(reader).getAssetCategories()).isEmpty();
        assertThat(new PatrimoinePersistenceAdapter(reader).getTransfers()).isEmpty();
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
        List<PlacementModel> placements = new PatrimoinePersistenceAdapter(reader).getPlacements();
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
    @DisplayName("DB-1021 -- tables autonomes vides au demarrage (donnees pre-existantes) -> reconstruites depuis le hub")
    void goalTablesAreRebuiltFromHubOnStartup() {
        writer.setBudgetData(referenceData());
        context.getBean(GoalRepository.class).deleteAll();
        assertThat(context.getBean(GoalRepository.class).count()).isZero();

        PersistenceManager restarted = freshReader();

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
                context.getBean(TaxChildRepository.class),
                context.getBean(TaxBracketRepository.class),
                context.getBean(TaxRateOverrideRepository.class),
                context.getBean(TaxActualOverrideRepository.class),
                context.getBean(AssetCategoryRepository.class),
                context.getBean(RetirementRepository.class),
                context.getBean(BankImportRepository.class),
                context.getBean(LoanRepository.class),
                context.getBean(ObjectifRepository.class),
                context.getBean(GoalRepository.class),
                context.getBean(CreditLoanRepository.class),
                transactionManager,
                eventPublisher);
        reader.init();
        return reader;
    }

    private static void assertReferenceState(PersistenceManager reader) {
        BudgetPersistenceAdapter budget = new BudgetPersistenceAdapter(reader);
        assertSameContent(budget.getIncomes(), List.of(INCOME));
        assertSameContent(budget.getCharges(), List.of(CHARGE));
        assertSameContent(budget.getOneoffExpenses(), List.of(ONEOFF));
        assertSameContent(budget.getVariableIncomes(), List.of(VARIABLE_INCOME));
        assertSameContent(budget.getVariableOverrides(), List.of(VARIABLE_OVERRIDE));

        PatrimoinePersistenceAdapter patrimoine = new PatrimoinePersistenceAdapter(reader);
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
        assertThat(settings.pass2026()).isEqualByComparingTo(SETTINGS.pass2026());
        assertThat(settings.passGrowthRate()).isEqualByComparingTo(SETTINGS.passGrowthRate());
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
            "manual", bd("5000"), 23, bd("0.05"), bd("47100"), bd("0.02"), null, null, null, null);

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
