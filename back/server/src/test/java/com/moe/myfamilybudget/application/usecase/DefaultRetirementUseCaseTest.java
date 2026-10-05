package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.usecase.retirement.RetraitePersonWithProjectionModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteResultModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetirementUseCase;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;
import com.moe.myfamilybudget.server.internal.testsupport.SettingsReaderTestFactory;

/**
 * SILO-300 (lot B, pilote Retraite) : le cas d'usage compose la réponse Retraite sans {@code ResponseEntity} ni
 * DTO, et traduit les types des silos vers les modèles de {@code application-api}.
 */
class DefaultRetirementUseCaseTest {

    private RetirementUseCase useCase;
    private RetirementCommandService commandService;

    @BeforeEach
    void setUp() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        RetirementPersistenceAdapter retirementAdapter = new RetirementPersistenceAdapter(persistenceManager);
        useCase = new DefaultRetirementUseCase(
                new RetirementInputFactory(),
                new DefaultRetirementCalculationService(),
                SettingsReaderTestFactory.of(persistenceManager),
                new SettingsPersistenceAdapter(persistenceManager),
                retirementAdapter,
                new TaxPersistenceAdapter(persistenceManager),
                new BudgetPersistenceAdapter(persistenceManager));
        commandService = new RetirementCommandService(retirementAdapter);
    }

    @Test
    @DisplayName("getRetraite() renvoie un modèle complet : retraite, année de départ, revenus et paramètres")
    void returnsACompleteResult() {
        RetraiteResultModel result = useCase.getRetraite();

        assertThat(result).isNotNull();
        assertThat(result.retirement()).isNotNull();
        assertThat(result.retirement().people()).isNotNull();
        assertThat(result.retireYear()).isNotNull();
        assertThat(result.incomes()).isNotNull();
        assertThat(result.settings()).isNotNull();
    }

    @Test
    @DisplayName("getRetraite() traduit les personnes, leur historique de salaires et leur projection")
    void translatesPeopleAndProjections() {
        commandService.updateRetirement(new RetirementModel(
                List.of(new RetirementModel.RetirementPersonModel(
                        "p1", "Jean Dupont", 1980, "", 120, "2025-01-01",
                        List.of(new RetirementModel.SalaryHistoryModel(2020, new BigDecimal("30000"))),
                        new BigDecimal("2000"), new BigDecimal("0.0051"), Boolean.TRUE)),
                new BigDecimal("48000"), new BigDecimal("0.02"), new BigDecimal("1.50"), "2026-01-01",
                new BigDecimal("0.012")));

        RetraiteResultModel result = useCase.getRetraite();

        assertThat(result.retirement().pass2026()).isEqualByComparingTo("48000");
        assertThat(result.retirement().passGrowthRate()).isEqualByComparingTo("0.02");
        assertThat(result.retirement().agircPointValue()).isEqualByComparingTo("1.50");
        assertThat(result.retirement().agircPointDateGlobal()).isEqualTo("2026-01-01");
        assertThat(result.retirement().agircPointGrowthRate()).isEqualByComparingTo("0.012");
        assertThat(result.retirement().people()).hasSize(1);

        RetraitePersonWithProjectionModel person = result.retirement().people().get(0);
        assertThat(person.id()).isEqualTo("p1");
        assertThat(person.name()).isEqualTo("Jean Dupont");
        assertThat(person.birthYear()).isEqualTo(1980);
        assertThat(person.cadre()).isTrue();
        assertThat(person.trimestresValides()).isEqualTo(120);
        assertThat(person.salaryHistory()).hasSize(1);
        assertThat(person.salaryHistory().get(0).year()).isEqualTo(2020);
        assertThat(person.salaryHistory().get(0).salary()).isEqualByComparingTo("30000");
        assertThat(person.projection()).isNotNull();
        assertThat(person.projection().trimestresValides()).isEqualTo(120);
        assertThat(person.projection().pensionTotaleAnnuelle()).isNotNull();
    }

    @Test
    @DisplayName("getRetraite() reprend les valeurs par défaut de la retraite quand elle est vide")
    void usesRetirementDefaultsWhenEmpty() {
        RetraiteResultModel result = useCase.getRetraite();

        assertThat(result.retirement().people()).isEmpty();
        assertThat(result.retirement().pass2026()).isNotNull();
        assertThat(result.retirement().passGrowthRate()).isNotNull();
        assertThat(result.retirement().agircPointValue()).isNotNull();
        assertThat(result.retirement().agircPointDateGlobal()).isNotNull();
    }
}
