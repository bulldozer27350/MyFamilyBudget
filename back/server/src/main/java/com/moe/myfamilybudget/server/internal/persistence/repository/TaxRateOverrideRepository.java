package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.TaxRateOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRateOverrideRepository extends JpaRepository<TaxRateOverrideEntity, Long> {
    
    List<TaxRateOverrideEntity> findByBudgetDataId(Long budgetDataId);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
