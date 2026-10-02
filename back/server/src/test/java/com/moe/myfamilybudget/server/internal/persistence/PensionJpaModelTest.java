package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.RetirementPersonModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.SalaryHistoryModel;
import com.moe.myfamilybudget.server.internal.persistence.converter.PensionEntityMapper;
import com.moe.myfamilybudget.server.internal.persistence.entity.PensionPersonEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.PensionPlanEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.PensionPlanRepository;

/**
 * DB-1000 : le modele JPA autonome du domaine Retraite (additif, sans lien avec le hub) doit restituer les
 * hypotheses, les personnes et leurs historiques de salaires a l'identique, dans l'ordre de saisie, sans
 * arrondir les taux, valeurs de point et ratios.
 */
@DataJpaTest
@DisplayName("PensionJpaModelTest -- modele JPA Retraite autonome (DB-1000)")
class PensionJpaModelTest {

    @Autowired
    private PensionPlanRepository repository;

    @Autowired
    private TestEntityManager em;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static SalaryHistoryModel salary(int year, String amount) {
        return new SalaryHistoryModel(year, bd(amount));
    }

    private static RetirementPersonModel person(String id, String name, SalaryHistoryModel... salaries) {
        return new RetirementPersonModel(id, name, 1975, "Salaire " + name, 120, "2025-01-01", List.of(salaries),
                bd("15234.50"), bd("0.00512345"), Boolean.TRUE);
    }

    private static RetirementModel plan(RetirementPersonModel... people) {
        return new RetirementModel(List.of(people), bd("47100.00"), bd("0.01500000"), bd("1.43860000"),
                "2025-11-01", bd("0.01000000"));
    }

    private RetirementModel saveAndReload(RetirementModel model) {
        repository.deleteAll();
        repository.flush();
        repository.save(PensionEntityMapper.toEntity(model));
        em.flush();
        em.clear();
        return PensionEntityMapper.toModel(repository.findFirstByOrderByIdAsc().orElseThrow());
    }

    private static void assertSameContent(RetirementModel actual, RetirementModel expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("Hypotheses, personnes et salaires sont relus a l'identique et dans l'ordre")
    void roundTripKeepsContentAndOrder() {
        RetirementModel model = plan(
                person("p-2", "Camille", salary(2024, "52000.00"), salary(2023, "50500.50"), salary(2025, "53000.00")),
                person("p-1", "Louis", salary(2025, "41000.00")),
                person("p-3", "Sans salaire"));

        RetirementModel reloaded = saveAndReload(model);

        assertSameContent(reloaded, model);
        assertThat(reloaded.people()).extracting(RetirementPersonModel::id).containsExactly("p-2", "p-1", "p-3");
        assertThat(reloaded.people().get(0).salaryHistory()).extracting(SalaryHistoryModel::year)
                .containsExactly(2024, 2023, 2025);
    }

    @Test
    @DisplayName("Valeurs de point, taux et ratios ne sont pas arrondis")
    void ratesAndPointValuesAreNotRounded() {
        RetirementModel reloaded = saveAndReload(plan(person("p-1", "Louis")));

        assertThat(reloaded.agircPointValue()).isEqualByComparingTo("1.4386");
        assertThat(reloaded.passGrowthRate()).isEqualByComparingTo("0.015");
        assertThat(reloaded.people().get(0).ratioPointsParEuro()).isEqualByComparingTo("0.00512345");
    }

    @Test
    @DisplayName("Champs optionnels absents conserves a null, sans defaut applique")
    void optionalFieldsStayNull() {
        RetirementPersonModel sparse = new RetirementPersonModel(null, null, null, null, null, null, null, null,
                null, null);
        RetirementModel model = new RetirementModel(List.of(sparse), null, null, null, null, null);

        RetirementModel reloaded = saveAndReload(model);

        assertThat(reloaded.pass2026()).isNull();
        assertThat(reloaded.agircPointValue()).isNull();
        RetirementPersonModel reloadedPerson = reloaded.people().get(0);
        assertThat(reloadedPerson.id()).isNull();
        assertThat(reloadedPerson.cadre()).isNull();
        assertThat(reloadedPerson.agircPoints()).isNull();
        assertThat(reloadedPerson.salaryHistory()).isEmpty();
    }

    @Test
    @DisplayName("Liste de personnes absente ou vide -> relue comme une liste vide")
    void emptyOrNullPeopleAreReadAsEmpty() {
        assertThat(saveAndReload(new RetirementModel(null, bd("47100"), null, null, null, null)).people())
                .isEmpty();
        assertThat(saveAndReload(new RetirementModel(List.of(), bd("47100"), null, null, null, null)).people())
                .isEmpty();
    }

    @Test
    @DisplayName("Mettre a jour une personne retire les salaires supprimes (orphelins)")
    void updateRemovesOrphanSalaries() {
        saveAndReload(plan(person("p-1", "Louis", salary(2023, "40000.00"), salary(2024, "41000.00"))));

        RetirementModel updated = plan(person("p-1", "Louis", salary(2024, "41500.00")));
        RetirementModel reloaded = saveAndReload(updated);

        assertSameContent(reloaded, updated);
        assertThat(em.getEntityManager().createQuery("select count(s) from PensionSalaryEntity s", Long.class)
                .getSingleResult()).isEqualTo(1L);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Supprimer l'hypothese retraite supprime personnes et salaires en cascade")
    void deleteCascadesToPeopleAndSalaries() {
        saveAndReload(plan(person("p-1", "Louis", salary(2024, "41000.00")), person("p-2", "Camille")));

        repository.deleteAll();
        em.flush();
        em.clear();

        assertThat(repository.count()).isZero();
        assertThat(em.getEntityManager().createQuery("select count(p) from PensionPersonEntity p", Long.class)
                .getSingleResult()).isZero();
        assertThat(em.getEntityManager().createQuery("select count(s) from PensionSalaryEntity s", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("Le mapper tolere null et relie les enfants a leur parent, sans dependance vers le hub")
    void mapperToleratesNullAndLinksChildren() {
        assertThat(PensionEntityMapper.toEntity(null)).isNull();
        assertThat(PensionEntityMapper.toModel(null)).isNull();

        PensionPlanEntity entity = PensionEntityMapper.toEntity(
                plan(person("p-1", "Louis", salary(2024, "41000.00"))));
        PensionPersonEntity child = entity.getPeople().get(0);
        assertThat(child.getPlan()).isSameAs(entity);
        assertThat(child.getPosition()).isZero();
        assertThat(child.getSalaryHistory().get(0).getPerson()).isSameAs(child);
    }
}
