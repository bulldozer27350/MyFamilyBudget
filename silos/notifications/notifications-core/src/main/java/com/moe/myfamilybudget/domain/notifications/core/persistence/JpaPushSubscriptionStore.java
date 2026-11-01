package com.moe.myfamilybudget.domain.notifications.core.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.notifications.model.PushSubscriptionModel;
import com.moe.myfamilybudget.domain.notifications.port.PushSubscriptionStore;

/**
 * Implémentation JPA de {@link PushSubscriptionStore} (même table qu'avant SILO-180).
 */
@Component
public class JpaPushSubscriptionStore implements PushSubscriptionStore {

    private final PushSubscriptionRepository repository;

    public JpaPushSubscriptionStore(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PushSubscriptionModel> findAll() {
        return repository.findAll().stream()
                .map(e -> new PushSubscriptionModel(e.getId(), e.getEndpoint(), e.getP256dh(), e.getAuth(),
                        e.getCreatedAt()))
                .toList();
    }

    @Override
    public boolean existsByEndpoint(String endpoint) {
        return repository.findByEndpoint(endpoint).isPresent();
    }

    @Override
    public void save(PushSubscriptionModel subscription) {
        repository.save(new PushSubscriptionEntity(subscription.id(), subscription.endpoint(),
                subscription.p256dh(), subscription.auth(), subscription.createdAt()));
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteByEndpoint(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
