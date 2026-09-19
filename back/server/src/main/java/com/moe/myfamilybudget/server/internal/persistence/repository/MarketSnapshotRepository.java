package com.moe.myfamilybudget.server.internal.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.MarketSnapshotEntity;

@Repository
public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshotEntity, String> {
}
