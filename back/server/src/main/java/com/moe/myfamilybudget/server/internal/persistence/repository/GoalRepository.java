package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.GoalEntity;

/**
 * Repository autonome du domaine Objectifs (DB-1020), sans lien avec {@code BudgetDataEntity}.
 * Lu par {@code GoalPersistenceAdapter} (DB-1021) ; alimente par {@code BudgetPersistenceGateway}.
 */
@Repository
public interface GoalRepository extends JpaRepository<GoalEntity, String> {

    List<GoalEntity> findAllByOrderByPositionAsc();
}
