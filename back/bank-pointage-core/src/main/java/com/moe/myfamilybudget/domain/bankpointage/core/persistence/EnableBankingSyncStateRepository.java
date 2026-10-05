package com.moe.myfamilybudget.domain.bankpointage.core.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EnableBankingSyncStateRepository extends JpaRepository<EnableBankingSyncStateEntity, String> {
}
