package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.LoanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepository extends JpaRepository<LoanEntity, Long> {

    @Modifying
    @Query("DELETE FROM LoanEntity l WHERE l.budgetData.id = :budgetDataId")
    void deleteByBudgetDataId(Long budgetDataId);

    java.util.List<LoanEntity> findByBudgetDataId(Long budgetDataId);
}
