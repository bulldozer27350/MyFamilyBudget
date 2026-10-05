package com.moe.myfamilybudget.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@DisplayName("SILO-205 -- SpringTransactionRunner")
class SpringTransactionRunnerTest {

    private PlatformTransactionManager manager;
    private TransactionStatus status;
    private SpringTransactionRunner runner;

    @BeforeEach
    void setUp() {
        manager = mock(PlatformTransactionManager.class);
        status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        runner = new SpringTransactionRunner(manager);
    }

    @Test
    @DisplayName("succès : le résultat est renvoyé et la transaction est validée")
    void commitsAndReturnsTheResult() {
        String result = runner.inTransaction(() -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(manager).commit(status);
        verify(manager, never()).rollback(any());
    }

    @Test
    @DisplayName("exception non contrôlée : la transaction est annulée et l'exception propagée telle quelle")
    void rollsBackOnRuntimeException() {
        IllegalStateException failure = new IllegalStateException("échec");

        assertThatThrownBy(() -> runner.inTransaction(() -> {
            throw failure;
        })).isSameAs(failure);

        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }

    @Test
    @DisplayName("la propagation est requise : on rejoint la transaction existante")
    void usesRequiredPropagation() {
        runner.inTransaction(() -> null);

        org.mockito.ArgumentCaptor<TransactionDefinition> captor =
                org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager).getTransaction(captor.capture());
        assertThat(captor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }
}
