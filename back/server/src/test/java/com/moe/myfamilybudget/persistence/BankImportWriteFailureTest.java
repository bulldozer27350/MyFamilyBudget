package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentEntity;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.JpaBankStore;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;

/**
 * FIX-010 -- Une ecriture BankImport en echec ne devient pas une reussite.
 *
 * <p>SILO-213 (lot B) : l'import bancaire s'ecrit directement par {@link JpaBankStore} dans la table autonome
 * {@code bank_import_document} ; il n'est plus reecrit avec le modele global. Le repository est mis en panne a
 * chaque etape (suppression, insertion) et l'exception d'origine doit remonter telle quelle, sans publier de
 * {@link BankImportMutatedEvent}.
 */
class BankImportWriteFailureTest {

    private static final IllegalStateException DB_DOWN = new IllegalStateException("simulated bank import failure");

    private static final BankImportModel NEW_BANK_IMPORT = new BankImportModel(List.of(), List.of(), List.of());

    private BankImportDocumentRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private JpaBankStore store;

    @BeforeEach
    void setUp() {
        repository = mock(BankImportDocumentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        store = new JpaBankStore(repository, eventPublisher);
    }

    @Test
    @DisplayName("Insertion du document en echec : l'exception d'origine remonte, aucun evenement")
    void documentInsertFailureIsPropagated() {
        doThrow(DB_DOWN).when(repository).save(any());

        assertEveryWriteFailsWithoutEvent();
    }

    @Test
    @DisplayName("Suppression du document precedent en echec : l'exception d'origine remonte, rien n'est insere")
    void documentDeleteFailureIsPropagated() {
        doThrow(DB_DOWN).when(repository).deleteAll();

        assertEveryWriteFailsWithoutEvent();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Une fois la base revenue, la meme ecriture aboutit et publie un seul evenement")
    void writeSucceedsOnceBankImportRecovers() {
        doThrow(DB_DOWN).when(repository).save(any());
        assertThatThrownBy(() -> store.updateBankImport(NEW_BANK_IMPORT)).isSameAs(DB_DOWN);
        verifyNoInteractions(eventPublisher);

        reset(repository);
        store.updateBankImport(NEW_BANK_IMPORT);

        verify(repository).save(any(BankImportDocumentEntity.class));
        verify(eventPublisher).publishEvent(any(BankImportMutatedEvent.class));
    }

    private void assertEveryWriteFailsWithoutEvent() {
        Map<String, Runnable> writes = new LinkedHashMap<>();
        writes.put("updateBankImport", () -> store.updateBankImport(NEW_BANK_IMPORT));
        writes.put("replace", () -> store.replace(NEW_BANK_IMPORT));
        writes.put("reset", store::reset);

        writes.forEach((name, write) -> assertThatThrownBy(write::run).as(name).isSameAs(DB_DOWN));

        verifyNoInteractions(eventPublisher);
    }
}
