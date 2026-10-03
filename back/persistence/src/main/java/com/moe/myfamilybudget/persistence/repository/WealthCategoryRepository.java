package com.moe.myfamilybudget.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.persistence.entity.WealthCategoryEntity;

/**
 * Repository autonome du domaine Patrimoine (DB-1050), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code PatrimoinePersistenceAdapter} (bascule en DB-1051).
 */
@Repository
public interface WealthCategoryRepository extends JpaRepository<WealthCategoryEntity, Long> {

    List<WealthCategoryEntity> findAllByOrderByPositionAsc();
}
