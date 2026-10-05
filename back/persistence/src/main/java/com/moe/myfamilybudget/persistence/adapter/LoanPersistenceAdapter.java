package com.moe.myfamilybudget.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanEntityMapper;
import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanRepository;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.credit.port.LoanWriter;
import com.moe.myfamilybudget.domain.credit.port.LoanSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link LoanReader} (RF-B00) et {@link LoanWriter} (DB-041).
 *
 * <p>DB-1041 : en production, la lecture passe par {@link CreditLoanRepository} (table autonome
 * {@code credit_loan}, DB-1040). Les ecritures passent toujours par le {@code PersistenceManager} (liste
 * {@code "loans"}) : la passerelle de persistance recopie les prets dans la table autonome dans la meme
 * transaction.
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class LoanPersistenceAdapter implements LoanReader, LoanWriter, LoanSnapshotWriter {

    private static final String LIST_KEY = "loans";

    private final PersistenceManager persistenceManager;
    private final CreditLoanRepository creditLoanRepository;

    public LoanPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null);
    }

    @Autowired
    public LoanPersistenceAdapter(PersistenceManager persistenceManager, CreditLoanRepository creditLoanRepository) {
        this.persistenceManager = persistenceManager;
        this.creditLoanRepository = creditLoanRepository;
    }

    @Override
    public List<LoanModel> getLoans() {
        if (creditLoanRepository == null) {
            return persistenceManager.getBudgetData().getEffectiveLoans();
        }
        return CreditLoanEntityMapper.toModels(creditLoanRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.savePatrimoineRow(LIST_KEY, body));
    }

    @Override
    public void deleteLoanRow(String id) {
        persistenceManager.write(m -> m.deletePatrimoineRow(LIST_KEY, id));
    }

    /** SILO-119 (lot B1) : import des prêts. */
    @Override
    public void replace(List<LoanModel> loans) {
        persistenceManager.write(m -> m.replaceLoansSnapshot(loans));
    }

    /** SILO-119 (lot B1) : suppression de tous les prêts. */
    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetLoansSnapshot());
    }
}
