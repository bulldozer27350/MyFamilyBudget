package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.WealthRealEstateEntity;

/**
 * Repository autonome du domaine Patrimoine (DB-1050), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code PatrimoinePersistenceAdapter} (bascule en DB-1051).
 */
@Repository
public interface WealthRealEstateRepository extends JpaRepository<WealthRealEstateEntity, Long> {

    List<WealthRealEstateEntity> findAllByOrderByPositionAsc();
}
