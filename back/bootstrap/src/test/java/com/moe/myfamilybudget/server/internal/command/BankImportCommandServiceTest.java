package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.domain.bankpointage.port.BankImportChange;
import com.moe.myfamilybudget.application.command.BankImportCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
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

    private BankReader reader;
    private BankWriter writer;
    private SiloMutationLock lock;
    private RecordingTransactionRunner transactions;
    private BankImportCommandService service;

    @BeforeEach
    void setUp() {
        reader = mock(BankReader.class);
        writer = mock(BankWriter.class);
        lock = mock(SiloMutationLock.class);
        transactions = RecordingTransactionRunner.direct();
        service = new BankImportCommandService(reader, writer, lock, transactions);
    }

    @Test
    @DisplayName("succes : l'import renvoye par la modification est transmis tel quel au port")
    void writeDelegates() {
        service.modifyBankImport(current -> BankImportChange.write(BANK_IMPORT, null));

        verify(writer).updateBankImport(BANK_IMPORT);
    }

    @Test
    @DisplayName("SILO-213 : l'ecriture s'execute dans la transaction, apres la prise du verrou du silo Banque")
    void writeRunsInTransactionAfterLockingBankSilo() {
        service.modifyBankImport(current -> BankImportChange.write(BANK_IMPORT, null));

        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.BANK_POINTAGE));
        order.verify(writer).updateBankImport(BANK_IMPORT);
    }

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailureIsPropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        doThrow(dbDown).when(writer).updateBankImport(any());

        assertThatThrownBy(() -> service.modifyBankImport(current -> BankImportChange.write(BANK_IMPORT, null)))
                .isSameAs(dbDown);
    }

    @Test
    @DisplayName("SILO-213 : modifyBankImport lit l'import apres la prise du verrou, puis ecrit le resultat")
    void modifyReadsUnderLockThenWrites() {
        when(reader.getBankImport()).thenReturn(BANK_IMPORT);
        BankImportModel updated = new BankImportModel(List.of(), List.of(), List.of());

        String result = service.modifyBankImport(current -> {
            assertThat(current).isSameAs(BANK_IMPORT);
            return BankImportChange.write(updated, "ok");
        });

        assertThat(result).isEqualTo("ok");
        assertThat(transactions.calls()).isEqualTo(1);
        InOrder order = inOrder(lock, reader, writer);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.BANK_POINTAGE));
        order.verify(reader).getBankImport();
        order.verify(writer).updateBankImport(updated);
    }

    @Test
    @DisplayName("SILO-213 : modifyBankImport sans import a ecrire renvoie le resultat sans ecriture")
    void modifyWithoutChangeDoesNotWrite() {
        when(reader.getBankImport()).thenReturn(BANK_IMPORT);

        String result = service.modifyBankImport(current -> BankImportChange.unchanged("introuvable"));

        assertThat(result).isEqualTo("introuvable");
        verify(writer, org.mockito.Mockito.never()).updateBankImport(any());
    }

    @Test
    @DisplayName("SILO-213 : une exception de la modification est propagee telle quelle, sans ecriture")
    void modifyFailureIsPropagatedWithoutWriting() {
        when(reader.getBankImport()).thenReturn(BANK_IMPORT);
        IllegalStateException refused = new IllegalStateException("refuse");

        assertThatThrownBy(() -> service.modifyBankImport(current -> {
            throw refused;
        })).isSameAs(refused);

        verify(writer, org.mockito.Mockito.never()).updateBankImport(any());
    }

    @Test
    @DisplayName("SILO-213 : une modification absente ou sans resultat est refusee")
    void modifyRejectsMissingModificationOrChange() {
        when(reader.getBankImport()).thenReturn(BANK_IMPORT);

        assertThatThrownBy(() -> service.modifyBankImport(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.modifyBankImport(current -> null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BankImportChange.write(null, "x")).isInstanceOf(IllegalArgumentException.class);

        verify(writer, org.mockito.Mockito.never()).updateBankImport(any());
    }

    @Test
    @DisplayName("SILO-213 : modifications simultanees de transactions distinctes -> aucune n'est perdue")
    void concurrentModificationsAreNotLost() throws Exception {
        InMemoryBankStore store = new InMemoryBankStore();
        ReentrantLock siloLock = new ReentrantLock();
        // Simule le verrou de silo tenu jusqu'a la fin de la transaction (comme SiloMutationLockRegistry).
        TransactionRunner serialized = new TransactionRunner() {
            @Override
            public <T> T inTransaction(Supplier<T> action) {
                siloLock.lock();
                try {
                    return action.get();
                } finally {
                    siloLock.unlock();
                }
            }
        };
        BankImportCommandService realService = new BankImportCommandService(store, store, silos -> { },
                serialized);
        int calls = 8;
        ExecutorService pool = Executors.newFixedThreadPool(calls);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                String id = "tx_" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    realService.modifyBankImport(current -> {
                        List<BankImportModel.BankTransactionModel> transactionsAfter =
                                new ArrayList<>(current.transactions());
                        transactionsAfter.add(new BankImportModel.BankTransactionModel(id, "2026-05-05", id, "CB",
                                BigDecimal.ONE, "", List.of()));
                        return BankImportChange.write(new BankImportModel(transactionsAfter,
                                current.categories(), current.matchings()), id);
                    });
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(store.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactlyInAnyOrder("tx_0", "tx_1", "tx_2", "tx_3", "tx_4", "tx_5", "tx_6", "tx_7");
    }

    @Test
    @DisplayName("integration adaptateur : l'import ecrit est relu par le lecteur Banque")
    void adapterWriteIsReadBackByReader() {
        InMemoryBankStore adapter = new InMemoryBankStore();
        BankImportCommandService realService = new BankImportCommandService(adapter, adapter, silos -> { },
                RecordingTransactionRunner.direct());
        assertThat(adapter.getBankImport().transactions()).isEmpty();

        realService.modifyBankImport(current -> BankImportChange.write(BANK_IMPORT, null));

        assertThat(adapter.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_1");
        assertThat(adapter.getBankImport().categories()).extracting(BankImportModel.CategoryModel::id)
                .containsExactly("cat_loyer");
        assertThat(adapter.events()).hasSize(1);
    }
}
