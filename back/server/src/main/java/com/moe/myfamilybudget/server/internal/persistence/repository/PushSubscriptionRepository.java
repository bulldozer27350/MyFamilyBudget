package com.moe.myfamilybudget.server.internal.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.moe.myfamilybudget.server.internal.persistence.entity.PushSubscriptionEntity;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, String> {

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
