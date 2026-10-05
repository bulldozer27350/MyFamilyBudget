package com.moe.myfamilybudget.domain.credit.core.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface LoanAdviceSettingsRepository extends JpaRepository<LoanAdviceSettingsEntity, String> {
}
