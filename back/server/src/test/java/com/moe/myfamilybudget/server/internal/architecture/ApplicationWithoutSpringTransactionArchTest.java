package com.moe.myfamilybudget.server.internal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Garde-fou SILO-205 (voir doc/architecture/21-plan-silotage.md) : {@code application} délimite ses transactions
 * par le port {@code TransactionRunner}, jamais par une annotation ou un type Spring de transaction.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationWithoutSpringTransactionArchTest {

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_SPRING_TRANSACTION = noClasses()
            .that().resideInAPackage("com.moe.myfamilybudget.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.transaction..", "jakarta.transaction..")
            .as("application n'importe ni org.springframework.transaction ni jakarta.transaction (SILO-205)");
}
