package com.moe.myfamilybudget.config;

import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.application.port.TransactionRunner;

/**
 * Implémentation Spring du port {@link TransactionRunner} (SILO-205) : un {@link TransactionTemplate} sur le
 * {@link PlatformTransactionManager} unique de l'application. Propagation requise et annulation sur exception
 * non contrôlée ou erreur : même sémantique que l'ancien {@code @Transactional} des services d'application.
 */
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> action) {
        return template.execute(status -> action.get());
    }
}
