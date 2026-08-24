package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<TransferEntity, Long> {
    
    List<TransferEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<TransferEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
