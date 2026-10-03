package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BankPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportDocumentRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowOneOffRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowTransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CreditLoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PensionPlanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthPlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.WealthRealEstateRepository;

/**
 * FIX-010 -- Une ecriture BankImport en echec ne devient pas une reussite en memoire.
 *
 * <p>{@code BudgetPersistenceGateway.saveBankImport} (table legacy {@code bank_import}) et
 * {@code syncBankImport} (table autonome {@code bank_import_document}) absorbaient auparavant les erreurs.
 * VT-330 avait laisse ce cas hors perimetre : les repositories BankImport y etaient des mocks inertes.
 * Ici, chacun des deux repositories est mis en panne a son tour et l'exception d'origine doit remonter
 * telle quelle, sans modifier l'etat memoire ni publier de {@link BudgetMutatedEvent}.
 */
class BankImportWriteFailureTest {

    private static final IllegalStateException DB_DOWN = new IllegalStateException("simulated bank import failure");

    private static final BankImportModel NEW_BANK_IMPORT = new BankImportModel(List.of(), List.of(), List.of());

    private BudgetDataRepository budgetDataRepository;
    private BankImportDocumentRepository bankImportDocumentRepository;
    private ApplicationEventPublisher eventPublisher;
    private PersistenceManager persistenceManager;

    private BudgetPersistenceAdapter budgetAdapter;
    private BankPersistenceAdapter bankAdapter;

    private BudgetDataModel before;

    @BeforeEach
    void setUp() {
        budgetDataRepository = mock(BudgetDataRepository.class);
        bankImportDocumentRepository = mock(BankImportDocumentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        doAnswer(invocation -> invocation.getArgument(0)).when(budgetDataRepository).save(any());

        persistenceManager = new PersistenceManager(
                budgetDataRepository,
                mock(SettingsRepository.class),
                mock(IncomeRepository.class),
                mock(ChargeRepository.class),
                mock(PlacementRepository.class),
                mock(RealEstateRepository.class),
                mock(OneOffExpenseRepository.class),
                mock(TransferRepository.class),
                mock(VariableIncomeRepository.class),
                mock(VariableOverrideRepository.class),
                mock(AssetCategoryRepository.class),
                mock(LoanRepository.class),
                mock(GoalRepository.class),
                mock(CreditLoanRepository.class),
                mock(FiscalChildRepository.class),
                mock(FiscalBracketRepository.class),
                mock(FiscalRateOverrideRepository.class),
                mock(FiscalActualOverrideRepository.class),
                mock(PensionPlanRepository.class),
                bankImportDocumentRepository,
                mock(WealthPlacementRepository.class),
                mock(WealthRealEstateRepository.class),
                mock(WealthCategoryRepository.class),
                mock(CashflowIncomeRepository.class),
                mock(CashflowChargeRepository.class),
                mock(CashflowOneOffRepository.class),
                mock(CashflowTransferRepository.class),
                mock(CashflowVariableIncomeRepository.class),
                mock(CashflowVariableOverrideRepository.class),
                mock(PlatformTransactionManager.class),
                eventPublisher);
        persistenceManager.init();

        budgetAdapter = new BudgetPersistenceAdapter(persistenceManager);
        bankAdapter = new BankPersistenceAdapter(persistenceManager);

        persistenceManager.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_seed", "label", "Salaire", "monthly", new BigDecimal("2500"))));

        before = persistenceManager.getBudgetData();
        clearInvocations(eventPublisher);
    }

    @Test
    @DisplayName("Table autonome bank_import_document en echec : l'exception d'origine remonte, la memoire reste inchangee")
    void documentTableFailureIsPropagated() {
        doThrow(DB_DOWN).when(bankImportDocumentRepository).save(any());

        assertEveryMutationFailsAndLeavesMemoryUntouched();
    }

    @Test
    @DisplayName("Suppression du document precedent en echec : l'exception d'origine remonte, la memoire reste inchangee")
    void documentTableDeleteFailureIsPropagated() {
        doThrow(DB_DOWN).when(bankImportDocumentRepository).deleteAll();

        assertEveryMutationFailsAndLeavesMemoryUntouched();
    }

    @Test
    @DisplayName("Une fois la base revenue, la meme mutation aboutit et publie un seul evenement")
    void writeSucceedsOnceBankImportRecovers() {
        doThrow(DB_DOWN).when(bankImportDocumentRepository).save(any());
        assertThatThrownBy(() -> persistenceManager.write(m -> m.updateBankImport(NEW_BANK_IMPORT))).isSameAs(DB_DOWN);
        verifyNoInteractions(eventPublisher);

        reset(bankImportDocumentRepository);
        persistenceManager.write(m -> m.updateBankImport(NEW_BANK_IMPORT));

        assertThat(bankAdapter.getBankImport()).isEqualTo(NEW_BANK_IMPORT);
        assertThat(persistenceManager.getBudgetData()).isNotSameAs(before);
        verify(eventPublisher).publishEvent(any(BudgetMutatedEvent.class));
    }

    /**
     * Toute sauvegarde reecrit l'integralite du modele, BankImport compris : une mutation sans rapport avec la
     * banque doit donc echouer elle aussi (et non reussir en memoire alors que la base est incoherente).
     */
    private void assertEveryMutationFailsAndLeavesMemoryUntouched() {
        List<IncomeModel> incomes = budgetAdapter.getIncomes();
        BankImportModel bankImport = bankAdapter.getBankImport();

        Map<String, Runnable> mutations = new LinkedHashMap<>();
        mutations.put("updateBankImport", () -> persistenceManager.write(m -> m.updateBankImport(NEW_BANK_IMPORT)));
        mutations.put("addTresorerieRow", () -> persistenceManager.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_new", "label", "Prime", "monthly", new BigDecimal("999")))));
        mutations.put("setBudgetData", () -> persistenceManager.setBudgetData(before.withBankImport(NEW_BANK_IMPORT)));

        mutations.forEach((name, mutation) -> {
            assertThatThrownBy(mutation::run).as(name).isSameAs(DB_DOWN);

            assertThat(persistenceManager.getBudgetData()).as(name).isSameAs(before);
            assertThat(budgetAdapter.getIncomes()).as(name).isEqualTo(incomes);
            assertThat(bankAdapter.getBankImport()).as(name).isSameAs(bankImport);
        });

        verifyNoInteractions(eventPublisher);
    }
}
