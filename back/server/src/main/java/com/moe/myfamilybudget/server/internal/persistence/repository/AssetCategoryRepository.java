package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.AssetCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetCategoryRepository extends JpaRepository<AssetCategoryEntity, Long> {
    
    List<AssetCategoryEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<AssetCategoryEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
