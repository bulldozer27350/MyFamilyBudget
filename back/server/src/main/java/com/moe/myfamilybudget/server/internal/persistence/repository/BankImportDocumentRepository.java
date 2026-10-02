package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.BankImportDocumentEntity;

/**
 * Repository autonome du domaine Banque (DB-1030), sans lien avec {@code BudgetDataEntity}.
 * Additif : pas encore branche sur {@code BankPersistenceAdapter} (bascule en DB-1031).
 */
@Repository
public interface BankImportDocumentRepository extends JpaRepository<BankImportDocumentEntity, Long> {

    Optional<BankImportDocumentEntity> findFirstByOrderByIdAsc();
}
