package com.moe.myfamilybudget.domain.bankpointage.core.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * tables relationnelles. R-01, correctif CI du 22 oct. 2026 (3e passe) : <strong>pas {@code @Lob}</strong>,
 * mais {@code @JdbcTypeCode(SqlTypes.LONG32VARCHAR)}. {@code @Lob} mappe {@code String} sur {@code oid} chez
 * PostgreSQL depuis Hibernate 5.6.2 (gestionnaire « Large Object », connexion non-autocommit exigee en
 * lecture : bug deja rencontre avec l'ancienne {@code BankImportEntity}), et un dialecte maison
 * (tentative precedente, {@code TextClobPostgreSQLDialect}) qui ne corrigeait que le DDL ({@code text} au
 * lieu d'{@code oid}) n'a pas suffi : Hibernate restait cable sur {@code ClobJdbcType} a l'execution
 * ({@code @Lob String} resout vers le type standard {@code materialized_clob}, qui fige sa lecture/ecriture
 * independamment du {@code JdbcTypeRegistry} du dialecte), d'ou le meme {@code PSQLException: Large Objects
 * may not be used in auto-commit mode} en lecture hors transaction explicite. {@code LONG32VARCHAR} est le
 * code dedie par Hibernate exactement pour ce cas (recommande par les mainteneurs Hibernate, cf.
 * hibernate/hibernate-orm#4361) : {@code text} en DDL chez PostgreSQL (longueur par defaut
 * {@code Length.LONG32}, pas de borne a fixer), {@code clob} chez H2 (aucun changement pour les tests), et
 * lecture/ecriture en chaine ordinaire ({@code getString}/{@code setString}), jamais via Large Object. Plus
 * besoin de dialecte maison : {@code application-docker.yml} repasse sur
 * {@code org.hibernate.dialect.PostgreSQLDialect} standard. La serialisation est portee par
 * {@code BankImportDocumentMapper}.
 */
@Entity
@Table(name = "bank_import_document")
public class BankImportDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @JdbcTypeCode(SqlTypes.LONG32VARCHAR), pas @Lob (risque documente plus haut) : text chez
    // PostgreSQL, clob chez H2, lecture/ecriture en chaine ordinaire (jamais de Large Object).
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "json_data", nullable = false)
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
