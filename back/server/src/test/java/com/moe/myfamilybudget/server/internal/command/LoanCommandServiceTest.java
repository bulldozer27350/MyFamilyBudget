package com.moe.myfamilybudget.server.internal.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.application.command.LoanCommandService;
import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.credit.port.LoanWriter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryLoanStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * DB-041 -- Le command service Credit valide la commande, delegue au port d'ecriture et laisse l'erreur de
 * persistance remonter telle quelle ; le store ecrit bien dans la table relue par le lecteur.
 *
 * <p>SILO-214 (lot B) : chaque ecriture s'execute dans une transaction, apres la prise du verrou du silo Credit.
 */
@DisplayName("DB-041 -- LoanCommandService")
class LoanCommandServiceTest {

    private LoanWriter writer;
    private SiloMutationLock lock;
    private RecordingTransactionRunner transactions;
    private LoanCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(LoanWriter.class);
        lock = mock(SiloMutationLock.class);
        transactions = RecordingTransactionRunner.direct();
        service = new LoanCommandService(writer, lock, transactions);
    }

    @Test
    @DisplayName("succes : sauvegarde et suppression sont transmises au port, le resultat est renvoye")
    void commandsDelegate() {
        Map<String, Object> body = Map.of("id", "loan_1", "label", "Test");
        Map<String, Object> saved = Map.of("id", "loan_1");
        when(writer.saveLoanRow(body)).thenReturn(saved);

        assertThat(service.saveLoanRow(body)).isSameAs(saved);
        service.deleteLoanRow("loan_1");

        verify(writer).deleteLoanRow("loan_1");
    }

    @Test
    @DisplayName("succes : un corps null reste accepte (creation par defaut, contrat historique)")
    void nullBodyIsAllowed() {
        service.saveLoanRow(null);

        verify(writer).saveLoanRow(null);
    }

    @Test
    @DisplayName("validation : un identifiant null est refuse, sans transaction, sans verrou et sans ecriture")
    void nullIdIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.deleteLoanRow(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(transactions.calls()).isZero();
        verifyNoInteractions(writer, lock);
    }

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailuresArePropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        when(writer.saveLoanRow(any())).thenThrow(dbDown);
        doThrow(dbDown).when(writer).deleteLoanRow(anyString());

        assertThatThrownBy(() -> service.saveLoanRow(Map.of())).isSameAs(dbDown);
        assertThatThrownBy(() -> service.deleteLoanRow("loan_1")).isSameAs(dbDown);
    }

    @Test
    @DisplayName("SILO-214 : la sauvegarde s'execute dans une transaction, silo Credit verrouille avant l'ecriture")
    void saveRunsInTransactionAfterLockingSilo() {
        Map<String, Object> body = Map.of("id", "loan_1", "label", "Test");

        service.saveLoanRow(body);

        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.CREDIT));
        order.verify(writer).saveLoanRow(body);
    }

    @Test
    @DisplayName("SILO-214 : la suppression s'execute dans une transaction, silo Credit verrouille avant l'ecriture")
    void deleteRunsInTransactionAfterLockingSilo() {
        service.deleteLoanRow("loan_1");

        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.CREDIT));
        order.verify(writer).deleteLoanRow("loan_1");
    }

    @Test
    @DisplayName("integration store : pret ecrit puis supprime, relu par le lecteur")
    void storeWritesAreReadBackByReader() {
        InMemoryLoanStore store = new InMemoryLoanStore();
        LoanCommandService realService = new LoanCommandService(store, silos -> { }, RecordingTransactionRunner.direct());

        realService.saveLoanRow(Map.of("id", "loan_1", "label", "Pret immo", "crd", new BigDecimal("180000"),
                "rate", new BigDecimal("0.0185"), "monthly", new BigDecimal("950")));

        assertThat(store.getLoans()).extracting(LoanModel::id).containsExactly("loan_1");

        realService.deleteLoanRow("loan_1");

        assertThat(store.getLoans()).isEmpty();
    }
}
