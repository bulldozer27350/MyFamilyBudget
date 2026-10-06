package com.moe.myfamilybudget.domain.bankpointage.core.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Repository autonome du domaine Banque (DB-1030), sans lien avec {@code BudgetDataEntity}.
 * Lu et ecrit directement par {@code JpaBankStore} (SILO-213, lot B).
 */
@Repository
public interface BankImportDocumentRepository extends JpaRepository<BankImportDocumentEntity, Long> {

    Optional<BankImportDocumentEntity> findFirstByOrderByIdAsc();
}
