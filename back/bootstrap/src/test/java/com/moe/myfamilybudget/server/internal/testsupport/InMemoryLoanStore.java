package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.List;
import java.util.Map;

import com.moe.myfamilybudget.domain.credit.core.persistence.JpaLoanStore;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.credit.port.LoanSnapshotWriter;
import com.moe.myfamilybudget.domain.credit.port.LoanWriter;

/**
 * Silo Credit en memoire pour les tests unitaires (SILO-214, lot B) : le vrai {@link JpaLoanStore} (memes
 * valeurs par defaut, meme lecture du corps, meme ordre) adosse a un {@link InMemoryLoanRepository}, sans base
 * ni {@code PersistenceManager}. Remplace l'ancien {@code LoanPersistenceAdapter}, qui lisait le cache global.
 */
public final class InMemoryLoanStore implements LoanReader, LoanWriter, LoanSnapshotWriter {

    private final JpaLoanStore delegate = new JpaLoanStore(InMemoryLoanRepository.create(), event -> { });

    @Override
    public List<LoanModel> getLoans() {
        return delegate.getLoans();
    }

    @Override
    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        return delegate.saveLoanRow(body);
    }

    @Override
    public void deleteLoanRow(String id) {
        delegate.deleteLoanRow(id);
    }

    @Override
    public void replace(List<LoanModel> loans) {
        delegate.replace(loans);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
