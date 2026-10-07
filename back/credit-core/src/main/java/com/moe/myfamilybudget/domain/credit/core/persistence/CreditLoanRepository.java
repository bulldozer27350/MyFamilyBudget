package com.moe.myfamilybudget.domain.credit.core.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository autonome du domaine Credit (DB-1040), sans lien avec {@code BudgetDataEntity}.
 * Lu et ecrit par {@code JpaLoanStore} (SILO-214, lot B) : plus aucune synchronisation depuis le cache global.
 */
@Repository
public interface CreditLoanRepository extends JpaRepository<CreditLoanEntity, String> {

    List<CreditLoanEntity> findAllByOrderByPositionAsc();
}
