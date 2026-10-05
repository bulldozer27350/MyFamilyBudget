package com.moe.myfamilybudget.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.moe.myfamilybudget.application.port.MutationSilo;

/**
 * SILO-206 -- Verrouillage par silo : serialisation par silo, parallelisme entre silos disjoints, verrou tenu
 * jusqu'a la fin de la transaction, ordre fixe sans interblocage. Pas de Spring ni de base : la transaction est
 * simulee avec {@link TransactionSynchronizationManager}, comme dans BudgetCacheStoreConcurrencyTest (VT-350).
 */
@Timeout(60)
@DisplayName("SILO-206 -- SiloMutationLockRegistry")
class SiloMutationLockRegistryTest {

    private static final long BLOCKED_MILLIS = 300;
    private static final long DONE_SECONDS = 10;

    private AtomicInteger legacyLockCalls;
    private SiloMutationLockRegistry registry;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        legacyLockCalls = new AtomicInteger();
        registry = new SiloMutationLockRegistry(legacyLockCalls::incrementAndGet);
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        clearSimulatedTransaction();
    }

    @Test
    @DisplayName("sans transaction ou avec un ensemble vide, rien n'est verrouille")
    void withoutTransactionNothingIsLocked() {
        assertThatCode(() -> {
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));
        }).doesNotThrowAnyException();

        beginSimulatedTransaction();
        registry.lockForCurrentTransaction(EnumSet.noneOf(MutationSilo.class));
        completeSimulatedTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(legacyLockCalls).hasValue(0);
    }

    @Test
    @DisplayName("deux transactions sur le meme silo sont serialisees jusqu'a la fin de la premiere")
    void sameSiloIsSerializedUntilTheTransactionEnds() throws Exception {
        CountDownLatch firstHolds = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> inSimulatedTransaction(() -> {
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));
            firstHolds.countDown();
            await(releaseFirst);
            return null;
        }));
        assertThat(firstHolds.await(DONE_SECONDS, TimeUnit.SECONDS)).isTrue();

        Future<?> second = executor.submit(() -> inSimulatedTransaction(() -> {
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));
            return null;
        }));
        assertThatThrownBy(() -> second.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        releaseFirst.countDown();
        first.get(DONE_SECONDS, TimeUnit.SECONDS);
        second.get(DONE_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("deux transactions sur des silos disjoints ne s'attendent pas")
    void disjointSilosDoNotWaitForEachOther() throws Exception {
        CountDownLatch firstHolds = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> inSimulatedTransaction(() -> {
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));
            firstHolds.countDown();
            await(releaseFirst);
            return null;
        }));
        assertThat(firstHolds.await(DONE_SECONDS, TimeUnit.SECONDS)).isTrue();

        Future<?> other = executor.submit(() -> inSimulatedTransaction(() -> {
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.GOALS));
            return null;
        }));
        other.get(DONE_SECONDS, TimeUnit.SECONDS);

        releaseFirst.countDown();
        first.get(DONE_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("le verrou est aussi relache apres une annulation")
    void lockIsReleasedAfterARollback() throws Exception {
        beginSimulatedTransaction();
        registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.WEALTH));
        completeSimulatedTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        Future<?> next = executor.submit(() -> inSimulatedTransaction(() -> {
            registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.WEALTH));
            return null;
        }));
        next.get(DONE_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("re-entrance : redemander un silo deja tenu est sans effet, le verrou global n'est pris qu'une fois")
    void reentranceTakesTheLegacyLockOnce() {
        beginSimulatedTransaction();
        registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.RETIREMENT, MutationSilo.TAX));
        registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));
        registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX, MutationSilo.GOALS));
        completeSimulatedTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(legacyLockCalls).hasValue(1);
    }

    @Test
    @DisplayName("ajouter un silo d'ordre inferieur a ceux deja tenus est refuse")
    void outOfOrderAcquisitionIsRejected() {
        beginSimulatedTransaction();
        registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.TAX));

        assertThatThrownBy(() -> registry.lockForCurrentTransaction(EnumSet.of(MutationSilo.RETIREMENT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIREMENT");
        completeSimulatedTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    @Test
    @DisplayName("des facades qui partagent plusieurs silos ne s'interbloquent pas (ordre fixe)")
    void facadesSharingSeveralSilosNeverDeadlock() throws Exception {
        int rounds = 100;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> facadeA = () -> {
            start.await();
            for (int i = 0; i < rounds; i++) {
                inSimulatedTransaction(() -> {
                    registry.lockForCurrentTransaction(
                            EnumSet.of(MutationSilo.RETIREMENT, MutationSilo.TAX, MutationSilo.GOALS));
                    return null;
                });
            }
            return null;
        };
        Callable<Void> facadeB = () -> {
            start.await();
            for (int i = 0; i < rounds; i++) {
                inSimulatedTransaction(() -> {
                    registry.lockForCurrentTransaction(
                            EnumSet.of(MutationSilo.GOALS, MutationSilo.TAX, MutationSilo.RETIREMENT));
                    return null;
                });
            }
            return null;
        };
        List<Future<Void>> futures = List.of(executor.submit(facadeA), executor.submit(facadeB));
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertThat(legacyLockCalls).hasValue(2 * rounds);
    }

    // --- transaction simulee -------------------------------------------------------------------------------

    private static void beginSimulatedTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeSimulatedTransaction(int status) {
        try {
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(status);
            }
        } finally {
            clearSimulatedTransaction();
        }
    }

    private static void clearSimulatedTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static <T> T inSimulatedTransaction(Callable<T> body) throws Exception {
        beginSimulatedTransaction();
        int status = TransactionSynchronization.STATUS_ROLLED_BACK;
        try {
            T result = body.call();
            status = TransactionSynchronization.STATUS_COMMITTED;
            return result;
        } finally {
            completeSimulatedTransaction(status);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(DONE_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("verrou de test non libere");
        }
    }
}
