package com.moe.myfamilybudget.domain.bankpointage.core.persistence;

import java.util.Collections;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportMutatedEvent;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.bankpointage.port.BankSnapshotWriter;
import com.moe.myfamilybudget.domain.bankpointage.port.BankWriter;

/**
 * Adaptateur JPA du silo Banque/Pointage (SILO-213, lot B) : implemente {@link BankReader}, {@link BankWriter}
 * et {@link BankSnapshotWriter} directement sur la table {@code bank_import_document}, sans passer par le cache
 * global ni par le {@code PersistenceManager}.
 *
 * <p>Les ecritures ne s'appellent que dans une transaction deja ouverte ({@code TransactionRunner}) et apres la
 * prise du verrou du silo ({@code MutationSilo.BANK_POINTAGE}) par l'appelant : l'adaptateur ne porte ni
 * {@code @Transactional} ni verrou. Chaque ecriture remplace le document par l'import resultant (suppression,
 * {@code flush}, insertion), comme l'ancienne synchronisation du modele global : le {@code flush} est
 * indispensable, Hibernate executant les insertions avant les suppressions. Une erreur de serialisation ou
 * d'ecriture est propagee a l'appelant ({@link IllegalStateException} pour le JSON) : une ecriture en echec ne
 * devient jamais une reussite.
 *
 * <p>Lecture : sans document en base, un import vide est restitue (aucun consommateur ne recoit {@code null}) ;
 * une erreur de relecture du JSON est propagee.
 *
 * <p>Apres chaque ecriture, un {@link BankImportMutatedEvent} est publie : l'ecoute apres commit declenche le
 * controle automatique des notifications, comme le faisait {@code BudgetMutatedEvent}.
 */
@Component
public class JpaBankStore implements BankReader, BankWriter, BankSnapshotWriter {

    private final BankImportDocumentRepository bankImportDocumentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JpaBankStore(BankImportDocumentRepository bankImportDocumentRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.bankImportDocumentRepository = bankImportDocumentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public BankImportModel getBankImport() {
        BankImportModel model = bankImportDocumentRepository.findFirstByOrderByIdAsc()
                .map(BankImportDocumentMapper::toModel)
                .orElse(null);
        return model != null ? model : emptyImport();
    }

    /** Un import {@code null} est ignore (aucune ecriture, aucun evenement), comme l'ancienne mutation. */
    @Override
    public void updateBankImport(BankImportModel bankImport) {
        if (bankImport == null) {
            return;
        }
        rewrite(bankImport);
        publishMutated("updateBankImport");
    }

    /** SILO-119 (lot B1) : import de l'import bancaire ({@code null} accepte : aucun document). */
    @Override
    public void replace(BankImportModel bankImport) {
        rewrite(bankImport);
        publishMutated("replace");
    }

    /** SILO-119 (lot B1) : remise a vide de l'import bancaire (un document vide est ecrit). */
    @Override
    public void reset() {
        rewrite(emptyImport());
        publishMutated("reset");
    }

    /** Remplace le contenu de la table par {@code bankImport} ({@code null} : table vide). */
    private void rewrite(BankImportModel bankImport) {
        bankImportDocumentRepository.deleteAll();
        bankImportDocumentRepository.flush();
        if (bankImport == null) {
            return;
        }
        bankImportDocumentRepository.save(BankImportDocumentMapper.toEntity(bankImport));
    }

    private static BankImportModel emptyImport() {
        return new BankImportModel(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new BankImportMutatedEvent(mutationKind));
    }
}
