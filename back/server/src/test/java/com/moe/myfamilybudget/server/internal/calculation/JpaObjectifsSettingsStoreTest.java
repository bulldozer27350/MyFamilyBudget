package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.moe.myfamilybudget.server.internal.persistence.entity.ObjectifsSettingsEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.ObjectifsSettingsRepository;

class JpaObjectifsSettingsStoreTest {

    private ObjectifsSettingsRepository repository;
    private JpaObjectifsSettingsStore store;

    @BeforeEach
    void setUp() {
        repository = mock(ObjectifsSettingsRepository.class);
        store = new JpaObjectifsSettingsStore(repository);
    }

    @Test
    @DisplayName("load() est vide quand aucune ligne n'est enregistrée")
    void testLoadEmpty() {
        when(repository.findById("current")).thenReturn(Optional.empty());

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("load() reconstruit les paramètres depuis la ligne unique 'current'")
    void testLoadExisting() {
        when(repository.findById("current"))
                .thenReturn(Optional.of(new ObjectifsSettingsEntity("current", 24, null, Instant.now())));

        assertThat(store.load()).contains(new ObjectifsParameters(24, null));
    }

    @Test
    @DisplayName("save() écrit une seule ligne 'current' avec les deux seuils")
    void testSave() {
        store.save(new ObjectifsParameters(18, 4));

        ArgumentCaptor<ObjectifsSettingsEntity> captor = ArgumentCaptor.forClass(ObjectifsSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("current");
        assertThat(captor.getValue().getSecureHorizonMonths()).isEqualTo(18);
        assertThat(captor.getValue().getLiquidHorizonMonths()).isEqualTo(4);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("clear() supprime la ligne 'current'")
    void testClear() {
        store.clear();

        verify(repository).deleteById("current");
    }
}
