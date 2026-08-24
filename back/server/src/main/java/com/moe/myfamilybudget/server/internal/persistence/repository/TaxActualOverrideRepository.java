package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.TaxActualOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxActualOverrideRepository extends JpaRepository<TaxActualOverrideEntity, Long> {
    
    List<TaxActualOverrideEntity> findByBudgetDataId(Long budgetDataId);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
