package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.RealEstateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RealEstateRepository extends JpaRepository<RealEstateEntity, Long> {
    
    List<RealEstateEntity> findByBudgetDataId(Long budgetDataId);
    
    Optional<RealEstateEntity> findByUid(String uid);
    
    void deleteByBudgetDataId(Long budgetDataId);
}
