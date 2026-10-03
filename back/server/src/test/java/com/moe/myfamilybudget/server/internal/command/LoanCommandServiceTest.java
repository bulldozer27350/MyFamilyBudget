package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.LoanCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.adapter.LoanPersistenceAdapter;
import com.moe.myfamilybudget.domain.credit.port.LoanWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-041 -- Le command service Credit valide la commande, delegue au port d'ecriture et laisse l'erreur de
 * persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le lecteur.
 */
@DisplayName("DB-041 -- LoanCommandService")
class LoanCommandServiceTest {

    private LoanWriter writer;
    private LoanCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(LoanWriter.class);
        service = new LoanCommandService(writer);
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
    @DisplayName("validation : un identifiant null est refuse et rien n'est ecrit")
    void nullIdIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.deleteLoanRow(null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
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
    @DisplayName("integration adaptateur : pret ecrit puis supprime, relu par le lecteur")
    void adapterWritesAreReadBackByReader() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        LoanPersistenceAdapter adapter = new LoanPersistenceAdapter(persistenceManager);
        LoanCommandService realService = new LoanCommandService(adapter);

        realService.saveLoanRow(Map.of("id", "loan_1", "label", "Pret immo", "crd", new BigDecimal("180000"), "rate", new BigDecimal("0.0185"), "monthly", new BigDecimal("950")));

        assertThat(adapter.getLoans()).extracting(LoanModel::id).containsExactly("loan_1");

        realService.deleteLoanRow("loan_1");

        assertThat(adapter.getLoans()).isEmpty();
    }
}
