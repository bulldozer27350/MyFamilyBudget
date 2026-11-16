package com.moe.myfamilybudget.server.internal.testsupport;

import java.util.ArrayList;
import java.util.List;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.bankpointage.port.BankSnapshotWriter;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;

/**
 * Silo Banque/Pointage en memoire pour les tests unitaires. Depuis l'etape 3/5 de la migration relationnelle
 * (areas/bank-pointage-relational-migration.md, cote projet), {@code JpaBankStore} reel n'a plus de
 * constructeur simple a adosser a un faux repository (il depend de dix repositories JPA) : ce doublure
 * reimplemente directement {@link BankReader}, {@link BankWriter} et {@link BankSnapshotWriter} sur un
 * simple champ {@link BankImportModel} en memoire, sans base ni Spring. Memes regles que le vrai silo (import
 * vide par defaut, jamais {@code null} ; {@code updateBankImport(null)} ignore ; {@code replace}/{@code reset}
 * remplacent sans condition). Les evenements publies sont conserves ({@link #events()}).
 */
public final class InMemoryBankStore implements BankReader, BankWriter, BankSnapshotWriter {

    private final List<BankImportMutatedEvent> events = new ArrayList<>();
    private BankImportModel bankImport = emptyImport();

    @Override
    public BankImportModel getBankImport() {
        return bankImport;
    }

    @Override
    public void updateBankImport(BankImportModel bankImport) {
        if (bankImport == null) {
            return;
        }
        this.bankImport = bankImport;
        events.add(new BankImportMutatedEvent("updateBankImport"));
    }

    @Override
    public void replace(BankImportModel bankImport) {
        this.bankImport = bankImport != null ? bankImport : emptyImport();
        events.add(new BankImportMutatedEvent("replace"));
    }

    @Override
    public void reset() {
        this.bankImport = emptyImport();
        events.add(new BankImportMutatedEvent("reset"));
    }

    /** Évènements {@link BankImportMutatedEvent} publiés depuis la création du silo, dans l'ordre. */
    public List<BankImportMutatedEvent> events() {
        return List.copyOf(events);
    }

    private static BankImportModel emptyImport() {
        return new BankImportModel(List.of(), List.of(), List.of());
    }
}
