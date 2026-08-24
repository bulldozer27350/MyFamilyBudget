package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.VariableOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariableOverrideRepository extends JpaRepository<VariableOverrideEntity, Long> {
    
    List<VariableOverrideEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<VariableOverrideEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
