package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.ChargeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeRepository extends JpaRepository<ChargeEntity, Long> {
    
    List<ChargeEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<ChargeEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
