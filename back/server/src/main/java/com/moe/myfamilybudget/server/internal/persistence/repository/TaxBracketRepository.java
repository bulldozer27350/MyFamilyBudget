package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.TaxBracketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxBracketRepository extends JpaRepository<TaxBracketEntity, Long> {
    
    List<TaxBracketEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<TaxBracketEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
