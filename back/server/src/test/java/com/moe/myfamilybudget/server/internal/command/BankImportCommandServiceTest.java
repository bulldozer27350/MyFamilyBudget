package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.BankImportCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BankPersistenceAdapter;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-040 -- Le command service Banque valide la commande, delegue au port d'ecriture et laisse l'erreur
 * de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le lecteur Banque.
 */
@DisplayName("DB-040 -- BankImportCommandService")
class BankImportCommandServiceTest {

    private static final BankImportModel BANK_IMPORT = new BankImportModel(
            List.of(new BankImportModel.BankTransactionModel("tx_1", "2026-05-05", "Paiement Loyer", "VIR",
                    new BigDecimal("-800"), "cat_loyer", List.of())),
            List.of(new BankImportModel.CategoryModel("cat_loyer", "Logement", "Depense", "Non")),
            List.of());

    private BankWriter writer;
    private BankImportCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(BankWriter.class);
        service = new BankImportCommandService(writer);
    }

    @Test
    @DisplayName("succes : l'import est transmis tel quel au port")
    void updateDelegates() {
        service.updateBankImport(BANK_IMPORT);

        verify(writer).updateBankImport(BANK_IMPORT);
    }

    @Test
    @DisplayName("validation : un import null est refuse et rien n'est ecrit")
    void nullImportIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.updateBankImport(null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailureIsPropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        doThrow(dbDown).when(writer).updateBankImport(any());

        assertThatThrownBy(() -> service.updateBankImport(BANK_IMPORT)).isSameAs(dbDown);
    }

    @Test
    @DisplayName("integration adaptateur : l'import ecrit est relu par le lecteur Banque")
    void adapterWriteIsReadBackByReader() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        BankPersistenceAdapter adapter = new BankPersistenceAdapter(persistenceManager);
        BankImportCommandService realService = new BankImportCommandService(adapter);
        assertThat(adapter.getBankImport().transactions()).isEmpty();

        realService.updateBankImport(BANK_IMPORT);

        assertThat(adapter.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_1");
        assertThat(adapter.getBankImport().categories()).extracting(BankImportModel.CategoryModel::id)
                .containsExactly("cat_loyer");
    }
}
