package com.moe.myfamilybudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;

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

    /**
     * R-60 : ne relaie plus le verrou global du cache (plus aucune écriture de production ne passe
     * par le modèle complet depuis R-20/R-30/R-40/R-42) ; {@code GlobalCacheLockRelay} est retiré.
     */
    @Bean
    public SiloMutationLock siloMutationLock() {
        return new SiloMutationLockRegistry();
    }
}
