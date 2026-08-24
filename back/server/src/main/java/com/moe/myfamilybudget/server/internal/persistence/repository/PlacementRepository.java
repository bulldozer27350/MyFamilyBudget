package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.PlacementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlacementRepository extends JpaRepository<PlacementEntity, Long> {
    
    List<PlacementEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<PlacementEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
