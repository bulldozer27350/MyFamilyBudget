package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.TaxChildEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxChildRepository extends JpaRepository<TaxChildEntity, Long> {
    
    List<TaxChildEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<TaxChildEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
