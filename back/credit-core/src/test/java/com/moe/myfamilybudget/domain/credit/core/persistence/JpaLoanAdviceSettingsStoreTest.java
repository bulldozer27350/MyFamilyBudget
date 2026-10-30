package com.moe.myfamilybudget.domain.credit.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;

class JpaLoanAdviceSettingsStoreTest {

    private LoanAdviceSettingsRepository repository;
    private ObjectMapper objectMapper;
    private JpaLoanAdviceSettingsStore store;

    @BeforeEach
    void setUp() {
        repository = mock(LoanAdviceSettingsRepository.class);
        objectMapper = new ObjectMapper();
        store = new JpaLoanAdviceSettingsStore(repository, objectMapper);
    }

    @Test
    @DisplayName("load() retourne vide quand aucune ligne n'est enregistrée")
    void loadEmpty() {
        when(repository.findById("current")).thenReturn(Optional.empty());

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("load() reconstruit les paramètres enregistrés")
    void loadExisting() {
        LoanAdviceParameters params = LoanAdviceParameters.defaults(new BigDecimal("0.035"));
        String json = LoanAdviceSettingsCodec.toJson(params, objectMapper);
        when(repository.findById("current")).thenReturn(Optional.of(new LoanAdviceSettingsEntity("current", json, Instant.now())));

        assertThat(store.load()).contains(params);
    }

    @Test
    @DisplayName("load() retourne vide et logge un avertissement sur un payload corrompu")
    void loadCorrupted() {
        when(repository.findById("current")).thenReturn(Optional.of(new LoanAdviceSettingsEntity("current", "not json", Instant.now())));

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("save() persiste une entité LoanAdviceSettingsEntity 'current'")
    void save() {
        LoanAdviceParameters params = LoanAdviceParameters.defaults(new BigDecimal("0.035"));
        store.save(params);

        ArgumentCaptor<LoanAdviceSettingsEntity> captor = ArgumentCaptor.forClass(LoanAdviceSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("current");
        assertThat(captor.getValue().getPayloadJson()).contains("0.035");
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }
}
