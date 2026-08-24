package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.IncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncomeRepository extends JpaRepository<IncomeEntity, Long> {
    
    List<IncomeEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<IncomeEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
