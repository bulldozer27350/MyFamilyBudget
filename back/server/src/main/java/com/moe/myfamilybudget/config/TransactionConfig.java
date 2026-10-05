package com.moe.myfamilybudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.persistence.adapter.GlobalCacheLockRelay;

/**
 * Câblage des ports {@link TransactionRunner} (SILO-205) et {@link SiloMutationLock} (SILO-206) de l'application ;
 * repris par le module bootstrap (SILO-330).
 */
@Configuration(proxyBeanMethods = false)
public class TransactionConfig {

    @Bean
    public TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringTransactionRunner(transactionManager);
    }

    /** Relais transitoire vers le verrou global du cache, retiré avec lui (SILO-230). */
    @Bean
    public SiloMutationLock siloMutationLock(GlobalCacheLockRelay globalCacheLockRelay) {
        return new SiloMutationLockRegistry(globalCacheLockRelay);
    }
}
