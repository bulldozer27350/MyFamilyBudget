package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.BankImportEntity;

@Repository
public interface BankImportRepository extends JpaRepository<BankImportEntity, Long> {
    Optional<BankImportEntity> findFirstByBudgetDataId(Long budgetDataId);
    void deleteByBudgetDataId(Long budgetDataId);
}
