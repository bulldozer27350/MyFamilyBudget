package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.JpaBankStore;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankDocumentRepository;

/**
 * SILO-213 (lot B) -- {@link JpaBankStore} : l'import bancaire s'ecrit et se relit directement dans sa table,
 * sans cache ; chaque ecriture remplace le document unique et publie un {@link BankImportMutatedEvent}.
 */
@DisplayName("SILO-213 -- JpaBankStore")
class JpaBankStoreTest {

    private static final BankImportModel BANK_IMPORT = new BankImportModel(
            List.of(new BankImportModel.BankTransactionModel("tx_1", "2026-05-05", "Paiement Loyer", "VIR",
                    new BigDecimal("-800"), "cat_loyer", List.of())),
            List.of(new BankImportModel.CategoryModel("cat_loyer", "Logement", "Depense", "Non")),
            List.of());

    private final List<Object> events = new ArrayList<>();
    private BankImportDocumentRepository repository;
    private JpaBankStore store;

    @BeforeEach
    void setUp() {
        repository = InMemoryBankDocumentRepository.create();
        store = new JpaBankStore(repository, events::add);
    }

    @Test
    @DisplayName("sans document : un import vide (jamais null) est restitue")
    void emptyImportWhenNoDocument() {
        BankImportModel bank = store.getBankImport();

        assertThat(bank).isNotNull();
        assertThat(bank.transactions()).isEmpty();
        assertThat(bank.categories()).isEmpty();
        assertThat(bank.matchings()).isEmpty();
    }

    @Test
    @DisplayName("updateBankImport : l'import est relu, un seul document, un evenement")
    void updateIsReadBack() {
        store.updateBankImport(BANK_IMPORT);

        assertThat(store.getBankImport().transactions()).extracting(BankImportModel.BankTransactionModel::id)
                .containsExactly("tx_1");
        assertThat(store.getBankImport().categories()).extracting(BankImportModel.CategoryModel::id)
                .containsExactly("cat_loyer");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(events).hasSize(1).allMatch(BankImportMutatedEvent.class::isInstance);
    }

    @Test
    @DisplayName("updateBankImport remplace le document precedent (une seule ligne)")
    void updateReplacesPreviousDocument() {
        store.updateBankImport(BANK_IMPORT);

        store.updateBankImport(new BankImportModel(List.of(), List.of(), List.of()));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(store.getBankImport().transactions()).isEmpty();
    }

    @Test
    @DisplayName("updateBankImport(null) est ignore : import conserve, aucun evenement")
    void updateNullIsIgnored() {
        store.updateBankImport(BANK_IMPORT);
        events.clear();

        store.updateBankImport(null);

        assertThat(store.getBankImport().transactions()).hasSize(1);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("replace(null) : aucun document, import vide restitue")
    void replaceNullClearsTable() {
        store.replace(BANK_IMPORT);

        store.replace(null);

        assertThat(repository.count()).isZero();
        assertThat(store.getBankImport().transactions()).isEmpty();
    }

    @Test
    @DisplayName("reset : un document vide est ecrit et un evenement est publie")
    void resetWritesEmptyDocument() {
        store.replace(BANK_IMPORT);
        events.clear();

        store.reset();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(store.getBankImport().transactions()).isEmpty();
        assertThat(events).hasSize(1);
    }
}
