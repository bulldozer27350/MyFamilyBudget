package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.persistence.converter.GoalEntityMapper;
import com.moe.myfamilybudget.persistence.entity.GoalAllocationEntity;
import com.moe.myfamilybudget.persistence.entity.GoalEntity;
import com.moe.myfamilybudget.persistence.repository.GoalRepository;

/**
 * DB-1020 : le modele JPA autonome du domaine Objectifs (additif, sans lien avec le hub) doit
 * restituer les objectifs et leurs allocations a l'identique, dans l'ordre de saisie.
 */
@DataJpaTest
@DisplayName("GoalJpaModelTest -- modele JPA Objectifs autonome (DB-1020)")
class GoalJpaModelTest {

    @Autowired
    private GoalRepository repository;

    @Autowired
    private TestEntityManager em;

    private static ObjectifModel goal(String id, String label, ObjectifAllocationModel... allocations) {
        return new ObjectifModel(id, label, new BigDecimal("10000.00"), null, "2030-06-01", null,
                "note " + id, List.of(allocations));
    }

    private static ObjectifAllocationModel alloc(String id, String placementId, String amount) {
        return new ObjectifAllocationModel(id, placementId, new BigDecimal(amount));
    }

    private List<ObjectifModel> saveAndReload(List<ObjectifModel> models) {
        repository.saveAll(GoalEntityMapper.toEntities(models));
        em.flush();
        em.clear();
        return GoalEntityMapper.toModels(repository.findAllByOrderByPositionAsc());
    }

    @Test
    @DisplayName("Objectifs et allocations multi-comptes sont relus a l'identique et dans l'ordre")
    void roundTripKeepsContentAndOrder() {
        List<ObjectifModel> models = List.of(
                goal("g-2", "Voyage", alloc("a-1", "pl-9", "1500.00"), alloc("a-2", "pl-3", "250.50")),
                goal("g-1", "Travaux"),
                goal("g-3", "Etudes", alloc("a-3", "pl-3", "300.00")));

        assertThat(saveAndReload(models)).isEqualTo(models);
    }

    @Test
    @DisplayName("Les champs historiques mono-compte et les champs optionnels vides sont conserves")
    void legacyAndOptionalFieldsAreKept() {
        ObjectifModel legacy = new ObjectifModel("g-legacy", "Ancien", new BigDecimal("5000.00"),
                new BigDecimal("1200.00"), "2028-01-01", "pl-1", null, List.of());
        ObjectifModel sparse = new ObjectifModel("g-sparse", null, null, null, null, null, null, null);

        List<ObjectifModel> reloaded = saveAndReload(List.of(legacy, sparse));

        assertThat(reloaded.get(0)).isEqualTo(legacy);
        assertThat(reloaded.get(1).id()).isEqualTo("g-sparse");
        assertThat(reloaded.get(1).targetAmount()).isNull();
        assertThat(reloaded.get(1).allocations()).isEmpty();
    }

    @Test
    @DisplayName("Mettre a jour un objectif retire les allocations supprimees (orphelines)")
    void updateRemovesOrphanAllocations() {
        saveAndReload(List.of(goal("g-1", "Voyage", alloc("a-1", "pl-1", "100.00"), alloc("a-2", "pl-2", "200.00"))));

        ObjectifModel updated = goal("g-1", "Voyage 2", alloc("a-2", "pl-2", "250.00"));
        List<ObjectifModel> reloaded = saveAndReload(List.of(updated));

        assertThat(reloaded).containsExactly(updated);
        assertThat(em.getEntityManager().createQuery("select count(a) from GoalAllocationEntity a", Long.class)
                .getSingleResult()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Supprimer un objectif supprime ses allocations en cascade")
    void deleteCascadesToAllocations() {
        saveAndReload(List.of(goal("g-1", "Voyage", alloc("a-1", "pl-1", "100.00"))));

        repository.deleteById("g-1");
        em.flush();
        em.clear();

        assertThat(repository.count()).isZero();
        assertThat(em.getEntityManager().createQuery("select count(a) from GoalAllocationEntity a", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("Le mapper ne porte aucune dependance vers le hub et refuse un objectif sans identifiant")
    void mapperRejectsGoalWithoutId() {
        assertThat(GoalEntityMapper.toEntity(null, 0)).isNull();
        assertThat(GoalEntityMapper.toModel((GoalEntity) null)).isNull();
        assertThatThrownBy(() -> GoalEntityMapper.toEntity(goal(null, "Sans id"), 0))
                .isInstanceOf(NullPointerException.class);
        GoalAllocationEntity child = GoalEntityMapper.toEntity(goal("g-1", "x", alloc("a-1", "pl-1", "1.00")), 0)
                .getAllocations().get(0);
        assertThat(child.getGoal().getId()).isEqualTo("g-1");
    }
}
