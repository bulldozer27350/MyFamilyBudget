package com.moe.myfamilybudget.server.internal.enablebanking;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.bankpointage.port.EnableBankingSyncStateStore;
import com.moe.myfamilybudget.persistence.entity.EnableBankingSyncStateEntity;
import com.moe.myfamilybudget.persistence.repository.EnableBankingSyncStateRepository;

/**
 * Implémentation JPA de {@link EnableBankingSyncStateStore} : une ligne par compte, remplacée à chaque
 * synchronisation (même table qu'avant SILO-170). Reste dans le composition root jusqu'à SILO-213.
 */
@Component
public class JpaEnableBankingSyncStateStore implements EnableBankingSyncStateStore {

    private final EnableBankingSyncStateRepository repository;

    public JpaEnableBankingSyncStateStore(EnableBankingSyncStateRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> findLastBookingDate(String accountUid) {
        return repository.findById(accountUid).map(EnableBankingSyncStateEntity::getLastBookingDate);
    }

    @Override
    public void save(String accountUid, String lastBookingDate, Instant lastSyncAt) {
        repository.save(new EnableBankingSyncStateEntity(accountUid, lastBookingDate, lastSyncAt));
    }
}
