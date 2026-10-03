package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;

/**
 * VT-350 -- Comportement de {@link BudgetCacheStore} quand plusieurs ecritures visent le meme budget en meme
 * temps. Pas de Spring ni de base : la passerelle est un mock, les threads sont lances en meme temps par un
 * verrou de depart.
 *
 * <p>Comportement documente (et protege par ces tests) :
 * <ul>
 *   <li>les mutations sont serialisees : chacune part de l'etat produit par la precedente, donc aucune mise a
 *       jour n'est perdue, y compris quand elles visent la meme ressource (lecture-modification-ecriture) ;</li>
 *   <li>deux ecritures absolues conflictuelles sur la meme valeur : la derniere sauvegardee gagne ; l'ordre
 *       n'est pas deterministe, mais l'etat final est toujours l'une des deux valeurs et la memoire est toujours
 *       identique a la derniere sauvegarde envoyee a la base ;</li>
 *   <li>import / reinitialisation contre mutation : le resultat est toujours l'un des deux ordres sequentiels
 *       possibles, jamais un melange ;</li>
 *   <li>avec une transaction active, le verrou est conserve jusqu'a sa fin (commit ou rollback) : une seconde
 *       mutation ne demarre pas tant que la premiere n'est pas validee (VT-350b) ;
 *   <li>une sauvegarde en echec ne bloque pas les autres et ne laisse rien en memoire ;</li>
 *   <li>un lecteur ne voit jamais un etat a moitie construit.</li>
 * </ul>
 */
@Timeout(60)
@DisplayName("VT-350 -- Concurrence sur BudgetCacheStore")
class BudgetCacheStoreConcurrencyTest {

    private BudgetPersistenceGateway gateway;
    private BudgetCacheStore store;
    private BudgetDataModel initial;

    @BeforeEach
    void setUp() {
        gateway = mock(BudgetPersistenceGateway.class);
        store = new BudgetCacheStore(gateway, null);
        store.init();
        initial = store.getBudgetData();
        // Les sauvegardes de l'initialisation ne comptent pas dans les verifications ci-dessous.
        clearInvocations(gateway);
    }

    @Test
    @DisplayName("Ajouts simultanes dans la meme liste : aucune mise a jour perdue")
    void concurrentAppendsLoseNothing() throws Exception {
        int threads = 8;
        int perThread = 25;
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            tasks.add(() -> {
                for (int i = 0; i < perThread; i++) {
                    String id = "inc_" + thread + "_" + i;
                    store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income(id, 1))));
                }
            });
        }

        assertThat(runConcurrently(tasks)).isEmpty();

        assertThat(ids(store.getBudgetData())).hasSize(threads * perThread).doesNotHaveDuplicates();
        verify(gateway, times(threads * perThread)).save(any());
    }

    @Test
    @DisplayName("Mutations simultanees sur deux listes differentes : les deux sont conservees")
    void concurrentMutationsOnDifferentListsAreBothKept() throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> {
            for (int i = 0; i < 50; i++) {
                String id = "inc_" + i;
                store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income(id, 1))));
            }
        });
        tasks.add(() -> {
            for (int i = 0; i < 50; i++) {
                String id = "inc_bis_" + i;
                store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income(id, 2))));
            }
        });

        assertThat(runConcurrently(tasks)).isEmpty();

        List<IncomeModel> incomes = store.getBudgetData().getEffectiveIncomes();
        assertThat(incomes).hasSize(100);
        assertThat(incomes.stream().filter(i -> i.id().startsWith("inc_bis_")).count()).isEqualTo(50);
    }

    @Test
    @DisplayName("Incrementations simultanees de la meme ligne : resultat deterministe, aucune perte")
    void concurrentReadModifyWriteOnSameRowIsDeterministic() throws Exception {
        store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1", 0))));
        clearInvocations(gateway);

        int threads = 8;
        int perThread = 25;
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                for (int i = 0; i < perThread; i++) {
                    store.applyAndPersist(b -> b.withIncomes(b.getEffectiveIncomes().stream()
                            .map(row -> row.id().equals("inc_1")
                                    ? income("inc_1", row.getEffectiveMonthly().intValue() + 1)
                                    : row)
                            .toList()));
                }
            });
        }

        assertThat(runConcurrently(tasks)).isEmpty();

        assertThat(store.getBudgetData().getEffectiveIncomes()).hasSize(1);
        assertThat(store.getBudgetData().getEffectiveIncomes().get(0).getEffectiveMonthly())
                .isEqualByComparingTo(String.valueOf(threads * perThread));
    }

    @RepeatedTest(20)
    @DisplayName("Ecritures absolues conflictuelles : l'une des deux valeurs gagne, la memoire suit la derniere sauvegarde")
    void conflictingAbsoluteWritesEndWithOneOfTheValues() throws Exception {
        store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1", 0))));
        clearInvocations(gateway);

        List<Runnable> tasks = List.of(
                () -> store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1", 111)))),
                () -> store.applyAndPersist(b -> b.withIncomes(List.of(income("inc_1", 222)))));

        assertThat(runConcurrently(tasks)).isEmpty();

        BudgetDataModel finalState = store.getBudgetData();
        assertThat(finalState.getEffectiveIncomes()).hasSize(1);
        assertThat(finalState.getEffectiveIncomes().get(0).getEffectiveMonthly().intValue()).isIn(111, 222);

        ArgumentCaptor<BudgetDataModel> saved = ArgumentCaptor.forClass(BudgetDataModel.class);
        verify(gateway, times(2)).save(saved.capture());
        assertThat(finalState).isSameAs(saved.getAllValues().get(1));
    }

    @Test
    @DisplayName("Sauvegardes en echec au milieu de mutations valides : rien ne fuit en memoire, personne n'est bloque")
    void failedSavesDoNotLeakNorBlockOthers() throws Exception {
        doAnswer(invocation -> {
            BudgetDataModel model = invocation.getArgument(0);
            if (model.getEffectiveIncomes().stream().anyMatch(i -> i.id().startsWith("poison"))) {
                throw new IllegalStateException("simulated database failure");
            }
            return null;
        }).when(gateway).save(any());

        int valid = 40;
        int poisoned = 10;
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < valid; i++) {
            String id = "inc_" + i;
            tasks.add(() -> store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income(id, 1)))));
        }
        for (int i = 0; i < poisoned; i++) {
            String id = "poison_" + i;
            tasks.add(() -> store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income(id, 1)))));
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertThat(failures).hasSize(poisoned).allMatch(IllegalStateException.class::isInstance);
        assertThat(ids(store.getBudgetData())).hasSize(valid).doesNotHaveDuplicates()
                .noneMatch(id -> id.startsWith("poison"));

        // Le verrou a bien ete relache apres chaque echec : une mutation ulterieure aboutit.
        BudgetDataModel next = store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_after", 1))));
        assertThat(ids(next)).hasSize(valid + 1);
    }

    @Test
    @DisplayName("Lecteurs pendant des ecritures : jamais d'etat a moitie construit, liste toujours croissante")
    void readersNeverSeeTornState() throws Exception {
        int writes = 100;
        int readers = 3;
        AtomicBoolean writerDone = new AtomicBoolean(false);

        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> {
            try {
                for (int i = 0; i < writes; i++) {
                    String id = "inc_" + i;
                    store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income(id, 1))));
                }
            } finally {
                writerDone.set(true);
            }
        });
        for (int r = 0; r < readers; r++) {
            tasks.add(() -> {
                int lastSize = 0;
                while (!writerDone.get()) {
                    List<String> seen = ids(store.getBudgetData());
                    assertThat(seen.size()).isGreaterThanOrEqualTo(lastSize);
                    for (int k = 0; k < seen.size(); k++) {
                        assertThat(seen.get(k)).isEqualTo("inc_" + k);
                    }
                    lastSize = seen.size();
                }
            });
        }

        assertThat(runConcurrently(tasks)).isEmpty();
        assertThat(ids(store.getBudgetData())).hasSize(writes);
    }

    @RepeatedTest(20)
    @DisplayName("Import contre mutation : le resultat est l'un des deux ordres sequentiels")
    void importRacingWithMutationEndsInASerialOrder() throws Exception {
        BudgetDataModel imported = initial.withIncomes(List.of(income("inc_import", 1)));

        List<Runnable> tasks = List.of(
                () -> store.setBudgetData(imported),
                () -> store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_new", 1)))));

        assertThat(runConcurrently(tasks)).isEmpty();

        BudgetDataModel finalState = store.getBudgetData();
        // import puis mutation : [import, new] ; mutation puis import : [import] (la mutation est ecrasee).
        assertThat(ids(finalState)).isIn(List.of("inc_import", "inc_new"), List.of("inc_import"));

        ArgumentCaptor<BudgetDataModel> saved = ArgumentCaptor.forClass(BudgetDataModel.class);
        verify(gateway, times(2)).save(saved.capture());
        assertThat(finalState).isSameAs(saved.getAllValues().get(1));
    }

    @RepeatedTest(20)
    @DisplayName("Reinitialisation contre mutation : le resultat est l'un des deux ordres sequentiels")
    void resetRacingWithMutationEndsInASerialOrder() throws Exception {
        List<Runnable> tasks = List.of(
                store::resetData,
                () -> store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_new", 1)))));

        assertThat(runConcurrently(tasks)).isEmpty();

        BudgetDataModel finalState = store.getBudgetData();
        // mutation puis reset : [] ; reset puis mutation : [new].
        assertThat(ids(finalState)).isIn(List.<String>of(), List.of("inc_new"));

        ArgumentCaptor<BudgetDataModel> saved = ArgumentCaptor.forClass(BudgetDataModel.class);
        verify(gateway, times(2)).save(saved.capture());
        assertThat(finalState).isSameAs(saved.getAllValues().get(1));
    }

    @Test
    @DisplayName("Transaction : le verrou est conserve jusqu'a la fin de la transaction, pas seulement pendant l'appel")
    void lockIsHeldUntilTransactionCompletion() throws Exception {
        CountDownLatch firstMutated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondEntered = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                TransactionSynchronizationManager.initSynchronization();
                try {
                    store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_a", 1))));
                    firstMutated.countDown();
                    releaseFirst.await();
                    complete(TransactionSynchronization.STATUS_COMMITTED);
                } finally {
                    clearSynchronization();
                }
                return null;
            });
            assertThat(firstMutated.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> {
                TransactionSynchronizationManager.initSynchronization();
                try {
                    store.applyAndPersist(b -> {
                        secondEntered.set(true);
                        return b.withIncomes(append(b.getEffectiveIncomes(), income("inc_b", 1)));
                    });
                    complete(TransactionSynchronization.STATUS_COMMITTED);
                } finally {
                    clearSynchronization();
                }
                return null;
            });

            Thread.sleep(300);
            // La premiere mutation est faite mais sa transaction n'est pas terminee : la seconde attend.
            assertThat(secondEntered.get()).isFalse();

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(secondEntered.get()).isTrue();
            assertThat(ids(store.getBudgetData())).containsExactly("inc_a", "inc_b");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Transaction annulee : la mutation suivante repart de l'etat d'avant, jamais de l'etat annule")
    void nextMutationStartsFromRestoredStateAfterRollback() throws Exception {
        CountDownLatch firstMutated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                TransactionSynchronizationManager.initSynchronization();
                try {
                    store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_a", 1))));
                    firstMutated.countDown();
                    releaseFirst.await();
                    complete(TransactionSynchronization.STATUS_ROLLED_BACK);
                } finally {
                    clearSynchronization();
                }
                return null;
            });
            assertThat(firstMutated.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> {
                TransactionSynchronizationManager.initSynchronization();
                try {
                    store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_b", 1))));
                    complete(TransactionSynchronization.STATUS_COMMITTED);
                } finally {
                    clearSynchronization();
                }
                return null;
            });

            Thread.sleep(300);
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(ids(store.getBudgetData())).containsExactly("inc_b");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("lockForCurrentTransaction : sans transaction, ne bloque rien et ne laisse aucun verrou")
    void lockForCurrentTransactionWithoutTransactionIsANoOp() throws Exception {
        store.lockForCurrentTransaction();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            BudgetDataModel updated = pool.submit(() ->
                    store.applyAndPersist(b -> b.withIncomes(append(b.getEffectiveIncomes(), income("inc_a", 1)))))
                    .get(10, TimeUnit.SECONDS);
            assertThat(ids(updated)).containsExactly("inc_a");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("lockForCurrentTransaction : le verrou est pris avant toute mutation et rendu a la fin de la transaction")
    void lockForCurrentTransactionHoldsLockUntilCompletion() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondEntered = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                TransactionSynchronizationManager.initSynchronization();
                try {
                    store.lockForCurrentTransaction();
                    locked.countDown();
                    releaseFirst.await();
                    complete(TransactionSynchronization.STATUS_COMMITTED);
                } finally {
                    clearSynchronization();
                }
                return null;
            });
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> {
                store.applyAndPersist(b -> {
                    secondEntered.set(true);
                    return b;
                });
                return null;
            });

            Thread.sleep(300);
            assertThat(secondEntered.get()).isFalse();

            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(secondEntered.get()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Lance toutes les taches au meme instant et retourne les exceptions levees. Echoue si une tache ne se
     * termine pas dans le delai (blocage).
     */
    private static List<Throwable> runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (Runnable task : tasks) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        task.run();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("tous les threads sont prets").isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("aucun blocage").isTrue();
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void complete(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
    }

    private static void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static List<IncomeModel> append(List<IncomeModel> list, IncomeModel added) {
        List<IncomeModel> copy = new ArrayList<>(list);
        copy.add(added);
        return copy;
    }

    private static List<String> ids(BudgetDataModel model) {
        return model.getEffectiveIncomes().stream().map(IncomeModel::id).toList();
    }

    private static IncomeModel income(String id, int monthly) {
        return new IncomeModel(id, "Salaire", BigDecimal.valueOf(monthly), "2026-01-01", "2053-12-31",
                BigDecimal.ZERO, "", "");
    }
}
