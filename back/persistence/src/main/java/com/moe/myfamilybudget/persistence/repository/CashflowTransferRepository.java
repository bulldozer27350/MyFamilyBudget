package com.moe.myfamilybudget.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.persistence.entity.CashflowTransferEntity;

/**
 * Repository autonome du domaine Tresorerie (DB-1060), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur les adapters (bascule en DB-1061).
 */
@Repository
public interface CashflowTransferRepository extends JpaRepository<CashflowTransferEntity, Long> {

    List<CashflowTransferEntity> findAllByOrderByPositionAsc();
}
