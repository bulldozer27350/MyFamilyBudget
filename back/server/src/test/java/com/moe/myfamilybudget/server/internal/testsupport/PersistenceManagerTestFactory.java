package com.moe.myfamilybudget.server.internal.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;
import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsRepository;
import com.moe.myfamilybudget.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.VariableOverrideRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

/**
 * Fabrique un {@link PersistenceManager} adossé à des repositories Spring Data entièrement
 * mockés (Mockito), pour les tests unitaires qui n'ont besoin que du cache mémoire
 * ({@code getBudgetData}/{@code setBudgetData}/{@code resetData}/{@code init}) sans base de
 * données réelle.
 *
 * <p>Remplace l'ancien constructeur sans argument de {@code PersistenceManager}, supprimé au
 * point 10 du plan de mitigation d'audit ({@code audit-mitigation-plan.md}) car il forçait la
 * présence, dans le code de production ({@code BudgetPersistenceGateway}), de seize gardes
 * {@code if (repository == null) return;} dédiées uniquement à ce mode de test.
 *
 * <p>Seul {@code budgetDataRepository.save(...)} est stubé : il doit renvoyer l'entité qu'on lui
 * passe, comme le ferait un vrai repository JPA après persistance. Sans ce stub, Mockito renvoie
 * {@code null} par défaut, et {@code BudgetPersistenceGateway.save(...)} se retrouverait avec une
 * entité {@code null} — ce qui lèverait une {@code NullPointerException} dès le premier enfant
 * sauvegardé (ex. {@code saveIncomes}, qui appelle {@code budgetData.getId()}). Tous les autres
 * repositories utilisent le comportement par défaut de Mockito (no-op pour les méthodes
 * {@code void}, {@code Optional.empty()}/{@code null} en retour), ce qui suffit :
 * {@code BudgetCacheStore} ne relit jamais la base après une écriture, il fait uniquement
 * confiance à l'état qu'il vient d'écrire dans son {@code AtomicReference}.
 */
public final class PersistenceManagerTestFactory {

    private PersistenceManagerTestFactory() {
    }

    /**
     * Construit un {@code PersistenceManager} prêt à l'emploi pour un test unitaire : cache
     * mémoire pleinement fonctionnel, aucune base de données réelle sollicitée. Peut être suivi
     * d'un appel à {@code init()} si le test en a besoin (ex. tests d'intégration légers
     * appelant explicitement le cycle de démarrage).
     */
    public static PersistenceManager inMemory() {
        BudgetDataRepository budgetDataRepository = mock(BudgetDataRepository.class);
        when(budgetDataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        return new PersistenceManager(
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
                mock(BankImportDocumentRepository.class),
                mock(WealthPlacementRepository.class),
                mock(WealthRealEstateRepository.class),
                mock(WealthCategoryRepository.class),
                mock(CashflowIncomeRepository.class),
                mock(CashflowChargeRepository.class),
                mock(CashflowOneOffRepository.class),
                mock(CashflowTransferRepository.class),
                mock(CashflowVariableIncomeRepository.class),
                mock(CashflowVariableOverrideRepository.class),
                mock(PensionSettingsRepository.class),
                mock(FiscalSettingsRepository.class),
                mock(CashflowSettingsRepository.class),
                mock(AppSettingsRepository.class),
                mock(PlatformTransactionManager.class),
                mock(ApplicationEventPublisher.class));
    }
}
