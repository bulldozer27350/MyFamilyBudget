package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.BankImportCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * DB-040 -- Le command service Banque valide la commande, delegue au port d'ecriture et laisse l'erreur
 * de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le lecteur Banque.
 *
 * <p>SILO-213 (lot B) : l'ecriture s'execute dans la transaction, apres la prise du verrou du silo
 * Banque/Pointage.
 */
@DisplayName("DB-040 -- BankImportCommandService")
class BankImportCommandServiceTest {

    private static final BankImportModel BANK_IMPORT = new BankImportModel(
            List.of(new BankImportModel.BankTransactionModel("tx_1", "2026-05-05", "Paiement Loyer", "VIR",
                    new BigDecimal("-800"), "cat_loyer", List.of())),
            List.of(new BankImportModel.CategoryModel("cat_loyer", "Logement", "Depense", "Non")),
            List.of());

    private BankWriter writer;
    private SiloMutationLock lock;
    private RecordingTransactionRunner transactions;
    private BankImportCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(BankWriter.class);
        lock = mock(SiloMutationLock.class);
        transactions = RecordingTransactionRunner.direct();
        service = new BankImportCommandService(writer, lock, transactions);
    }

    @Test
    @DisplayName("succes : l'import est transmis tel quel au port")
    void updateDelegates() {
        service.updateBankImport(BANK_IMPORT);

        verify(writer).updateBankImport(BANK_IMPORT);
    }

    @Test
    @DisplayName("SILO-213 : l'ecriture s'execute dans la transaction, apres la prise du verrou du silo Banque")
    void updateRunsInTransactionAfterLockingBankSilo() {
        service.updateBankImport(BANK_IMPORT);

        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.BANK_POINTAGE));
        order.verify(writer).updateBankImport(BANK_IMPORT);
    }

    @Test
    @DisplayName("validation : un import null est refuse, sans transaction, verrou ni ecriture")
    void nullImportIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.updateBankImport(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(transactions.calls()).isZero();
        verifyNoInteractions(writer, lock);
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
        InMemoryBankStore adapter = new InMemoryBankStore();
        BankImportCommandService realService = new BankImportCommandService(adapter, silos -> { },
                RecordingTransactionRunner.direct());
        assertThat(adapter.getBankImport().transactions()).isEmpty();

        realService.updateBankImport(BANK_IMPORT);

        assertThat(adapter.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_1");
        assertThat(adapter.getBankImport().categories()).extracting(BankImportModel.CategoryModel::id)
                .containsExactly("cat_loyer");
        assertThat(adapter.events()).hasSize(1);
    }
}
