package com.moe.myfamilybudget.domain.goals.core.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ObjectifsSettingsRepository extends JpaRepository<ObjectifsSettingsEntity, String> {
}
