package com.moe.myfamilybudget.persistence.repository;

import com.moe.myfamilybudget.persistence.entity.SettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettingsRepository extends JpaRepository<SettingsEntity, Long> {
}
