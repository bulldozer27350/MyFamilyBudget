package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.ArrayList;
import java.util.List;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.JpaBankStore;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.bankpointage.port.BankSnapshotWriter;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;

/**
 * Silo Banque/Pointage en memoire pour les tests unitaires (SILO-213, lot B) : le vrai {@link JpaBankStore}
 * (meme serialisation JSON, meme import vide par defaut) adosse a un {@link InMemoryBankDocumentRepository},
 * sans base ni {@code PersistenceManager}. Remplace l'ancien {@code BankPersistenceAdapter}, qui lisait et
 * ecrivait par le cache global. Les evenements publies sont conserves ({@link #events()}).
 */
public final class InMemoryBankStore implements BankReader, BankWriter, BankSnapshotWriter {

    private final List<BankImportMutatedEvent> events = new ArrayList<>();
    private final JpaBankStore delegate = new JpaBankStore(InMemoryBankDocumentRepository.create(),
            event -> events.add((BankImportMutatedEvent) event));

    @Override
    public BankImportModel getBankImport() {
        return delegate.getBankImport();
    }

    @Override
    public void updateBankImport(BankImportModel bankImport) {
        delegate.updateBankImport(bankImport);
    }

    @Override
    public void replace(BankImportModel bankImport) {
        delegate.replace(bankImport);
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    /** Evenements {@link BankImportMutatedEvent} publies depuis la creation du silo, dans l'ordre. */
    public List<BankImportMutatedEvent> events() {
        return List.copyOf(events);
    }
}
