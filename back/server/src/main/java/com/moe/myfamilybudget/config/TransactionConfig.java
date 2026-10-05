package com.moe.myfamilybudget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.application.port.TransactionRunner;

/** Câblage du port {@link TransactionRunner} de l'application (SILO-205) ; repris par le module bootstrap (SILO-330). */
@Configuration(proxyBeanMethods = false)
public class TransactionConfig {

    @Bean
    public TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringTransactionRunner(transactionManager);
    }
}
