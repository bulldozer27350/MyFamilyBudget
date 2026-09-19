package com.moe.myfamilybudget.server.internal.marketdata;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.persistence.entity.MarketSnapshotEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.MarketSnapshotRepository;

/**
 * Implémentation JPA de {@link MarketSnapshotStore} : une seule ligne, remplacée à chaque
 * rafraîchissement réussi.
 */
@Component
public class JpaMarketSnapshotStore implements MarketSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(JpaMarketSnapshotStore.class);
    private static final String SNAPSHOT_ID = "current";

    private final MarketSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public JpaMarketSnapshotStore(MarketSnapshotRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MarketSnapshot> load() {
        return repository.findById(SNAPSHOT_ID).flatMap(entity -> {
            try {
                return Optional.of(MarketSnapshotCodec.fromJson(entity.getPayloadJson(), objectMapper));
            } catch (MarketDataException e) {
                log.warn("Instantané de marché persisté ignoré : {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public void save(MarketSnapshot snapshot) {
        repository.save(new MarketSnapshotEntity(
                SNAPSHOT_ID, MarketSnapshotCodec.toJson(snapshot, objectMapper), snapshot.fetchedAt()));
    }
}
