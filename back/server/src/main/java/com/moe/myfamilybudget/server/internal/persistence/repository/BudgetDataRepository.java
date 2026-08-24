package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.BudgetDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetDataRepository extends JpaRepository<BudgetDataEntity, Long> {
    
    Optional<BudgetDataEntity> findFirstByOrderByIdAsc();
}
