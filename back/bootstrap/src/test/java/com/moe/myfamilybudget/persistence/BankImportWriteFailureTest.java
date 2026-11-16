package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankColumnMappingRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankMatchingLinkRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankMatchingLinkTxRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankMatchingRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankPendingOperationRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankPendingOperationSplitRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankRuleRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankTransactionRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankTransactionSplitRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.JpaBankStore;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;

/**
 * FIX-010 -- Une écriture BankImport en échec ne devient pas une réussite.
 *
 * <p>Étape 3/5 de la migration relationnelle (areas/bank-pointage-relational-migration.md, côté projet) :
 * {@link JpaBankStore} écrit désormais directement dans le schéma relationnel (dix repositories JPA), et
 * non plus dans le document JSON unique {@code bank_import_document}. Chaque repository est mis en panne
 * à son tour (suppression, insertion) et l'exception d'origine doit remonter telle quelle, sans publier de
 * {@link BankImportMutatedEvent} — qu'il s'agisse de la première table touchée (aucune autre table n'est
 * alors modifiée) ou d'une table touchée après que d'autres ont déjà été vidées/réécrites (l'essentiel pour
 * la non-perte de données n'est pas qu'aucune table ne soit modifiée, mais qu'aucune réussite partielle ne
 * soit jamais confondue avec un succès).
 */
class BankImportWriteFailureTest {

    private static final IllegalStateException DB_DOWN = new IllegalStateException("simulated bank import failure");

    private static final BankImportModel NEW_BANK_IMPORT = new BankImportModel(List.of(), List.of(), List.of());

    private BankColumnMappingRepository columnMappingRepository;
    private BankCategoryRepository categoryRepository;
    private BankRuleRepository ruleRepository;
    private BankTransactionRepository transactionRepository;
    private BankTransactionSplitRepository transactionSplitRepository;
    private BankPendingOperationRepository pendingOperationRepository;
    private BankPendingOperationSplitRepository pendingOperationSplitRepository;
    private BankMatchingRepository matchingRepository;
    private BankMatchingLinkRepository matchingLinkRepository;
    private BankMatchingLinkTxRepository matchingLinkTxRepository;
    private ApplicationEventPublisher eventPublisher;
    private JpaBankStore store;

    @BeforeEach
    void setUp() {
        columnMappingRepository = mock(BankColumnMappingRepository.class);
        categoryRepository = mock(BankCategoryRepository.class);
        ruleRepository = mock(BankRuleRepository.class);
        transactionRepository = mock(BankTransactionRepository.class);
        transactionSplitRepository = mock(BankTransactionSplitRepository.class);
        pendingOperationRepository = mock(BankPendingOperationRepository.class);
        pendingOperationSplitRepository = mock(BankPendingOperationSplitRepository.class);
        matchingRepository = mock(BankMatchingRepository.class);
        matchingLinkRepository = mock(BankMatchingLinkRepository.class);
        matchingLinkTxRepository = mock(BankMatchingLinkTxRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        store = new JpaBankStore(columnMappingRepository, categoryRepository, ruleRepository, transactionRepository,
                transactionSplitRepository, pendingOperationRepository, pendingOperationSplitRepository,
                matchingRepository, matchingLinkRepository, matchingLinkTxRepository, eventPublisher);
    }

    @Test
    @DisplayName("Suppression de la premiere table en echec : l'exception d'origine remonte, rien n'est insere nulle part")
    void firstTableDeleteFailureIsPropagated() {
        doThrow(DB_DOWN).when(matchingLinkTxRepository).deleteAllInBatch();

        assertEveryWriteFailsWithoutEvent();
        verify(transactionRepository, never()).saveAll(anyList());
        verify(categoryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Suppression d'une table enfant deja videe, mais une table parent echoue : l'exception remonte, rien n'est insere")
    void laterTableDeleteFailureIsPropagated() {
        doThrow(DB_DOWN).when(categoryRepository).deleteAllInBatch();

        assertEveryWriteFailsWithoutEvent();
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Insertion en echec (table transaction) : l'exception d'origine remonte, aucun evenement")
    void transactionInsertFailureIsPropagated() {
        doThrow(DB_DOWN).when(transactionRepository).saveAll(anyList());

        assertEveryWriteFailsWithoutEvent();
    }

    @Test
    @DisplayName("Une fois la base revenue, la meme ecriture aboutit et publie un seul evenement")
    void writeSucceedsOnceBankImportRecovers() {
        doThrow(DB_DOWN).when(transactionRepository).saveAll(anyList());
        assertThatThrownBy(() -> store.updateBankImport(NEW_BANK_IMPORT)).isSameAs(DB_DOWN);
        verifyNoInteractions(eventPublisher);

        reset(transactionRepository);
        store.updateBankImport(NEW_BANK_IMPORT);

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
