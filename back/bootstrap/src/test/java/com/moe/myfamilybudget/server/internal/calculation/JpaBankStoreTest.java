package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankMatchingLinkRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankMatchingLinkTxRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankMatchingRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankTransactionRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankTransactionSplitRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.JpaBankStore;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.domain.bankpointage.model.BankTransactionPage;

/**
 * Étape 3/5 de la migration relationnelle de l'import bancaire (areas/bank-pointage-relational-migration.md,
 * côté projet) : {@link JpaBankStore} sur le schéma relationnel (tables {@code bank_transaction},
 * {@code bank_category}, etc., étape 1/5), et non plus le document JSON unique {@code bank_import_document}.
 *
 * <p>Remplace l'ancien test du même nom (un {@link JpaBankStore} adossé à un faux
 * {@code BankImportDocumentRepository} en mémoire) : le nouveau constructeur dépend de dix repositories JPA
 * réels, ce qu'une simple doublure en mémoire ne peut plus représenter fidèlement (contraintes de clé
 * étrangère entre tables, identifiants générés pour les liens de rapprochement). Utilise donc une vraie base
 * H2, comme {@code PersistenceAdaptersJpaRoundTripTest} pour les autres silos.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:jpabankstoretest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@RecordApplicationEvents
@DisplayName("Étape 3 -- JpaBankStore (schéma relationnel)")
class JpaBankStoreTest {

    private static final BankImportModel.BankTransactionSplitModel TX_SPLIT =
            new BankImportModel.BankTransactionSplitModel("split_1", "cat_courses", new BigDecimal("30"), "Part courses");

    private static final BankImportModel BANK_IMPORT = new BankImportModel(
            List.of(
                    new BankImportModel.BankTransactionModel("tx_1", "2026-05-05", "Paiement Loyer", "VIR",
                            new BigDecimal("-800"), "cat_loyer", List.of()),
                    new BankImportModel.BankTransactionModel("tx_2", "2026-05-06", "Supermarche", "CB",
                            new BigDecimal("-80"), null, List.of(TX_SPLIT))),
            List.of(
                    new BankImportModel.CategoryModel("cat_loyer", "Logement", "Depense", "Non"),
                    new BankImportModel.CategoryModel("cat_courses", "Courses", "Depense", "Oui")),
            List.of(
                    new BankImportModel.MatchingModel("2026-05", List.of(
                            new BankImportModel.MatchingLinkModel("ligne_loyer", List.of("tx_1"))))));

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    void startFromBlankDatabase() {
        bankStore().reset();
    }

    private long mutatedEventCount() {
        return events.stream(BankImportMutatedEvent.class).count();
    }

    @Test
    @DisplayName("sans donnees : un import vide (jamais null) est restitue")
    void emptyImportByDefault() {
        BankImportModel bank = bankStore().getBankImport();

        assertThat(bank).isNotNull();
        assertThat(bank.transactions()).isEmpty();
        assertThat(bank.categories()).isEmpty();
        assertThat(bank.matchings()).isEmpty();
    }

    @Test
    @DisplayName("updateBankImport : l'import est relu a l'identique (transactions, categories, repartition, rapprochement), un evenement")
    void updateIsReadBack() {
        bankStore().updateBankImport(BANK_IMPORT);

        BankImportModel reread = bankStore().getBankImport();
        assertThat(reread.transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactlyInAnyOrder("tx_1", "tx_2");
        assertThat(reread.categories()).extracting(BankImportModel.CategoryModel::id)
                .containsExactlyInAnyOrder("cat_loyer", "cat_courses");
        assertThat(reread.transactions()).filteredOn(tx -> tx.id().equals("tx_2"))
                .singleElement()
                .satisfies(tx -> {
                    assertThat(tx.categoryId()).isEmpty(); // null cote modele -> "" (jamais null)
                    assertThat(tx.splits()).extracting(BankImportModel.BankTransactionSplitModel::id)
                            .containsExactly("split_1");
                });
        assertThat(reread.matchings()).singleElement().satisfies(m -> {
            assertThat(m.month()).isEqualTo("2026-05");
            assertThat(m.links()).singleElement().satisfies(link -> {
                assertThat(link.budgetLineId()).isEqualTo("ligne_loyer");
                assertThat(link.txIds()).containsExactly("tx_1");
            });
        });

        assertThat(transactionRepository().count()).isEqualTo(2);
        assertThat(categoryRepository().count()).isEqualTo(2);
        assertThat(transactionSplitRepository().count()).isEqualTo(1);
        assertThat(matchingRepository().count()).isEqualTo(1);
        assertThat(matchingLinkRepository().count()).isEqualTo(1);
        assertThat(matchingLinkTxRepository().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateBankImport remplace integralement l'import precedent (aucune ligne residuelle dans aucune table)")
    void updateReplacesPreviousImport() {
        bankStore().updateBankImport(BANK_IMPORT);

        bankStore().updateBankImport(new BankImportModel(List.of(), List.of(), List.of()));

        assertThat(bankStore().getBankImport().transactions()).isEmpty();
        assertThat(transactionRepository().count()).isZero();
        assertThat(categoryRepository().count()).isZero();
        assertThat(transactionSplitRepository().count()).isZero();
        assertThat(matchingRepository().count()).isZero();
        assertThat(matchingLinkRepository().count()).isZero();
        assertThat(matchingLinkTxRepository().count()).isZero();
    }

    @Test
    @DisplayName("updateBankImport(null) est ignore : import conserve, aucun evenement supplementaire")
    void updateNullIsIgnored() {
        bankStore().updateBankImport(BANK_IMPORT);
        long eventsBefore = mutatedEventCount();

        bankStore().updateBankImport(null);

        assertThat(bankStore().getBankImport().transactions()).hasSize(2);
        assertThat(mutatedEventCount()).isEqualTo(eventsBefore);
    }

    @Test
    @DisplayName("replace(null) : toutes les tables sont videes, import vide restitue")
    void replaceNullClearsAllTables() {
        bankStore().replace(BANK_IMPORT);

        bankStore().replace(null);

        assertThat(transactionRepository().count()).isZero();
        assertThat(categoryRepository().count()).isZero();
        assertThat(bankStore().getBankImport().transactions()).isEmpty();
    }

    @Test
    @DisplayName("reset : toutes les tables sont videes et un evenement est publie")
    void resetClearsAllTablesAndPublishesEvent() {
        bankStore().replace(BANK_IMPORT);
        long eventsBefore = mutatedEventCount();

        bankStore().reset();

        assertThat(transactionRepository().count()).isZero();
        assertThat(bankStore().getBankImport().transactions()).isEmpty();
        assertThat(mutatedEventCount()).isGreaterThan(eventsBefore);
    }

    @Test
    @DisplayName("étape 4 -- findTransactions filtre par categorisation, pagine et inclut les repartitions")
    void findTransactionsFiltersAndPaginatesWithSplits() {
        bankStore().updateBankImport(BANK_IMPORT);

        BankTransactionPage uncategorized = bankStore().findTransactions(false, 0, 10);
        assertThat(uncategorized.transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_2");
        assertThat(uncategorized.transactions().get(0).splits())
                .extracting(BankImportModel.BankTransactionSplitModel::id)
                .containsExactly("split_1");
        assertThat(uncategorized.totalElements()).isEqualTo(1);

        BankTransactionPage categorized = bankStore().findTransactions(true, 0, 10);
        assertThat(categorized.transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_1");

        BankTransactionPage firstOfOne = bankStore().findTransactions(null, 0, 1);
        assertThat(firstOfOne.transactions()).hasSize(1);
        assertThat(firstOfOne.totalElements()).isEqualTo(2);
        assertThat(firstOfOne.totalPages()).isEqualTo(2);
    }

    private JpaBankStore bankStore() {
        return context.getBean(JpaBankStore.class);
    }

    private BankTransactionRepository transactionRepository() {
        return context.getBean(BankTransactionRepository.class);
    }

    private BankTransactionSplitRepository transactionSplitRepository() {
        return context.getBean(BankTransactionSplitRepository.class);
    }

    private BankCategoryRepository categoryRepository() {
        return context.getBean(BankCategoryRepository.class);
    }

    private BankMatchingRepository matchingRepository() {
        return context.getBean(BankMatchingRepository.class);
    }

    private BankMatchingLinkRepository matchingLinkRepository() {
        return context.getBean(BankMatchingLinkRepository.class);
    }

    private BankMatchingLinkTxRepository matchingLinkTxRepository() {
        return context.getBean(BankMatchingLinkTxRepository.class);
    }
}
