package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.ApplicationContext;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;

/**
 * SILO-119 (lot B2) : l'import et le reset enchainent plusieurs {@code BudgetPersistenceGateway.save} dans UNE
 * transaction (un {@code replace} par silo). Un second {@code save} ne doit pas laisser des enfants geres qui
 * designent la ligne {@code budget_data} qu'il vient de supprimer ({@code TransientObjectException} a l'auto-flush).
 *
 * <p>{@code @DataJpaTest} englobe chaque test dans une transaction unique : c'est exactement le cas de l'import.
 */
@DataJpaTest
@DisplayName("BudgetPersistenceGatewayRepeatedSaveTest -- plusieurs save dans la meme transaction (SILO-119 B2)")
class BudgetPersistenceGatewayRepeatedSaveTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private BudgetDataRepository budgetDataRepository;

    @Autowired
    private LoanRepository loanRepository;

    /** Le constructeur du gateway attend une trentaine de repositories : on les resout par type. */
    private BudgetPersistenceGateway gateway() throws Exception {
        Constructor<?> constructor = BudgetPersistenceGateway.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] args = Arrays.stream(constructor.getParameterTypes()).map(context::getBean).toArray();
        return (BudgetPersistenceGateway) constructor.newInstance(args);
    }

    private static BudgetDataModel modelWithIncomes(String... ids) {
        BudgetDataModel base = new BudgetCacheStore(null, null).createDefaultBudgetData();
        List<IncomeModel> incomes = Arrays.stream(ids)
                .map(id -> new IncomeModel(id, "Salaire", new BigDecimal("3000"), "2026-01-01", "2053-12-31",
                        new BigDecimal("0.01"), "", ""))
                .toList();
        return base.withIncomes(incomes);
    }

    @Test
    @DisplayName("Deux save successifs avec des revenus dans la meme transaction : pas d'exception, une seule ligne")
    void successiveSavesInOneTransaction() throws Exception {
        BudgetPersistenceGateway gateway = gateway();

        gateway.save(modelWithIncomes("inc_1"));
        gateway.save(modelWithIncomes("inc_1"));
        gateway.save(modelWithIncomes("inc_1", "inc_2"));
        em.flush();
        em.clear();

        assertThat(budgetDataRepository.count()).isEqualTo(1);
        assertThat(incomeRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("save puis deleteAll puis save dans la meme transaction")
    void saveDeleteAllSaveInOneTransaction() throws Exception {
        BudgetPersistenceGateway gateway = gateway();

        gateway.save(modelWithIncomes("inc_1"));
        gateway.deleteAll();
        gateway.save(modelWithIncomes("inc_1"));
        em.flush();
        em.clear();

        assertThat(budgetDataRepository.count()).isEqualTo(1);
        assertThat(incomeRepository.count()).isEqualTo(1);
    }

    private static LoanModel loan(String id) {
        return new LoanModel(id, "Pret immo", new BigDecimal("100000"), new BigDecimal("0.02"),
                new BigDecimal("700"), new BigDecimal("30"), "2020-01-01", "2040-01-01");
    }

    @Test
    @DisplayName("Un budget relu depuis la base avec des prets (collection EAGER chargee) peut etre resauvegarde")
    void saveAfterReloadWithLoans() throws Exception {
        BudgetPersistenceGateway gateway = gateway();

        gateway.save(modelWithIncomes("inc_1").withLoans(List.of(loan("loan_1"))));
        em.flush();
        em.clear();

        gateway.save(modelWithIncomes("inc_1").withLoans(List.of(loan("loan_1"), loan("loan_2"))));
        gateway.save(modelWithIncomes("inc_1").withLoans(List.of(loan("loan_2"))));
        em.flush();
        em.clear();

        assertThat(budgetDataRepository.count()).isEqualTo(1);
        assertThat(loanRepository.count()).isEqualTo(1);
    }
}
