package com.moe.myfamilybudget.persistence.adapter;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentMapper;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;
import com.moe.myfamilybudget.domain.bankpointage.port.BankSnapshotWriter;

/**
 * Adaptateur de persistance pour {@link BankReader} (RF-B00) et {@link BankWriter} (DB-040).
 *
 * <p>DB-1031 : en production, la lecture passe par {@link BankImportDocumentRepository} (table autonome
 * {@code bank_import_document}, DB-1030). Les ecritures passent toujours par le {@code PersistenceManager} : la
 * passerelle de persistance recopie l'import bancaire dans la table autonome dans la meme transaction. Sans
 * document en base, un import vide est restitue (comme au chargement du cache : aucun consommateur ne recoit
 * {@code null}). Une erreur de relecture du JSON est propagee ({@link IllegalStateException}).
 *
 * <p>Le constructeur sans repository conserve l'ancienne lecture depuis le cache memoire ; il sert aux tests
 * unitaires adosses a des repositories mockes et constitue le chemin de retour arriere.
 */
@Component
public class BankPersistenceAdapter implements BankReader, BankWriter, BankSnapshotWriter {

    private final PersistenceManager persistenceManager;
    private final BankImportDocumentRepository bankImportDocumentRepository;

    public BankPersistenceAdapter(PersistenceManager persistenceManager) {
        this(persistenceManager, null);
    }

    @Autowired
    public BankPersistenceAdapter(PersistenceManager persistenceManager,
                                  BankImportDocumentRepository bankImportDocumentRepository) {
        this.persistenceManager = persistenceManager;
        this.bankImportDocumentRepository = bankImportDocumentRepository;
    }

    @Override
    public BankImportModel getBankImport() {
        if (bankImportDocumentRepository == null) {
            return persistenceManager.getBankImport();
        }
        BankImportModel model = bankImportDocumentRepository.findFirstByOrderByIdAsc()
                .map(BankImportDocumentMapper::toModel)
                .orElse(null);
        return model != null ? model
                : new BankImportModel(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public void updateBankImport(BankImportModel bankImport) {
        persistenceManager.write(m -> m.updateBankImport(bankImport));
    }

    /** SILO-119 (lot B1) : import de l'import bancaire. */
    @Override
    public void replace(BankImportModel bankImport) {
        persistenceManager.write(m -> m.replaceBankImportSnapshot(bankImport));
    }

    /** SILO-119 (lot B1) : remise à vide de l'import bancaire. */
    @Override
    public void reset() {
        persistenceManager.write(m -> m.resetBankImportSnapshot());
    }
}
