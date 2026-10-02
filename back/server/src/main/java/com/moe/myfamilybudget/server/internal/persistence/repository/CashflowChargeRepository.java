package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.CashflowChargeEntity;

/**
 * Repository autonome du domaine Tresorerie (DB-1060), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur les adapters (bascule en DB-1061).
 */
@Repository
public interface CashflowChargeRepository extends JpaRepository<CashflowChargeEntity, Long> {

    List<CashflowChargeEntity> findAllByOrderByPositionAsc();
}
