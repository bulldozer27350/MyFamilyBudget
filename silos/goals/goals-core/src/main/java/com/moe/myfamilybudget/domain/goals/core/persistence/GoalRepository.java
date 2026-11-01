package com.moe.myfamilybudget.domain.goals.core.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Repository autonome du domaine Objectifs (DB-1020), sans lien avec {@code BudgetDataEntity}.
 * Lu et ecrit par {@link JpaGoalStore} (SILO-212, lot B1) ; relu au chargement du cache par
 * {@code BudgetPersistenceGateway}.
 */
@Repository
public interface GoalRepository extends JpaRepository<GoalEntity, String> {

    List<GoalEntity> findAllByOrderByPositionAsc();
}
