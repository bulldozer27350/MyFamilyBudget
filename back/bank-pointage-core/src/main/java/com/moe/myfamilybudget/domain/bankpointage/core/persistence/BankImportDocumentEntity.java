package com.moe.myfamilybudget.domain.bankpointage.core.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Import bancaire (categories, regles, transactions, operations en attente, rapprochements), cible JPA
 * autonome du domaine Banque (DB-1030).
 *
 * <p>Cette entite n'a <strong>aucune relation vers {@code BudgetDataEntity}</strong> : elle est la racine de
 * son propre agregat (singleton fonctionnel : une seule ligne, lue par {@code findFirstByOrderByIdAsc}). Elle est
 * lue et ecrite par {@code JpaBankStore} (lecture depuis DB-1031, ecriture directe depuis SILO-213, lot B) et,
 * depuis DB-1130, c'est aussi la seule source de chargement du cache : l'ancienne table {@code bank_import} et
 * son entite ont ete supprimees.
 *
 * <p>Le format JSON reste une decision interne a Banque : le contenu n'est volontairement pas eclate en
 * tables relationnelles. Il est stocke en colonne {@code TEXT} explicite (pas {@code @Lob}) pour eviter le
 * mecanisme PostgreSQL « Large Object » (colonne {@code oid}), qui exige une connexion non-autocommit pour la
 * lecture (cas de l'ancienne {@code BankImportEntity}). La serialisation est portee par {@code BankImportDocumentMapper}.
 */
@Entity
@Table(name = "bank_import_document")
public class BankImportDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "json_data", columnDefinition = "TEXT", nullable = false)
    private String jsonData;

    public BankImportDocumentEntity() {}

    public BankImportDocumentEntity(String jsonData) {
        this.jsonData = jsonData;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJsonData() {
        return jsonData;
    }

    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }
}
