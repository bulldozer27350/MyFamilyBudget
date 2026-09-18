package com.moe.myfamilybudget.server.internal.persistence.repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.ObjectifEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectifRepository extends JpaRepository<ObjectifEntity, Long> {

    List<ObjectifEntity> findByBudgetDataId(Long budgetDataId);

    Optional<ObjectifEntity> findByUid(String uid);

    void deleteByBudgetDataId(Long budgetDataId);
}
