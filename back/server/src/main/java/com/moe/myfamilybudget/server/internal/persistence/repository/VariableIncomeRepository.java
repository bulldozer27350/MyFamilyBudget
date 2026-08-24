package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.VariableIncomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariableIncomeRepository extends JpaRepository<VariableIncomeEntity, Long> {
    
    List<VariableIncomeEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<VariableIncomeEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
