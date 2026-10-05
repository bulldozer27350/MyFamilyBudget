package com.moe.myfamilybudget.domain.market.core.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshotEntity, String> {
}
