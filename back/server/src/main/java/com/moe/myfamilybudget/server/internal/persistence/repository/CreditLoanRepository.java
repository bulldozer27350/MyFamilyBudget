package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.CreditLoanEntity;

/**
 * Repository autonome du domaine Credit (DB-1040), sans lien avec {@code BudgetDataEntity}.
 * Lu par {@code LoanPersistenceAdapter} (DB-1041) ; alimente par {@code BudgetPersistenceGateway}.
 */
@Repository
public interface CreditLoanRepository extends JpaRepository<CreditLoanEntity, String> {

    List<CreditLoanEntity> findAllByOrderByPositionAsc();
}
