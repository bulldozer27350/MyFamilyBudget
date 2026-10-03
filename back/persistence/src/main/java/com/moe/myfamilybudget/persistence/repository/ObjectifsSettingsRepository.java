package com.moe.myfamilybudget.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.persistence.entity.ObjectifsSettingsEntity;

@Repository
public interface ObjectifsSettingsRepository extends JpaRepository<ObjectifsSettingsEntity, String> {
}
