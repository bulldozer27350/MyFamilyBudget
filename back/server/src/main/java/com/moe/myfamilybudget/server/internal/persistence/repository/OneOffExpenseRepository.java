package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.OneOffExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OneOffExpenseRepository extends JpaRepository<OneOffExpenseEntity, Long> {
    
    List<OneOffExpenseEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<OneOffExpenseEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
