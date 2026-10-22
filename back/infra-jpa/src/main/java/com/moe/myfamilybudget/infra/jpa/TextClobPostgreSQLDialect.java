package com.moe.myfamilybudget.infra.jpa;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.SqlTypes;

/**
 * Dialecte PostgreSQL qui mappe {@code @Lob String} sur {@code text}, pas {@code oid}.
 *
 * <p>Depuis Hibernate 5.6.2 (<a href="https://in.relation.to/2021/12/08/hibernate-orm-562/">note de
 * version</a>), {@code PostgreSQLDialect} mappe {@code Types.CLOB} sur {@code oid} par defaut (pour
 * permettre le streaming via Large Object), au lieu de {@code text} comme avant. Les silos de ce projet
 * n'ont jamais eu besoin de streaming sur leurs colonnes JSON ({@code json_data}, {@code payload_json}) :
 * le comportement {@code oid} exige une connexion non-autocommit pour la lecture et a deja cause un bug
 * (ancienne {@code BankImportEntity}, cf. Javadoc de {@code BankImportDocumentEntity} dans
 * {@code bank-pointage-core}). D'ou ce dialecte, qui restaure le mapping {@code text} pre-5.6.2 pour
 * {@code @Lob}, sans toucher au reste du dialecte PostgreSQL standard. Utilise uniquement en profil
 * {@code docker} (PostgreSQL de prod) ; H2 (profil par defaut, tests) n'a pas ce probleme, {@code CLOB}
 * y reste {@code CLOB}.
 *
 * <p>R-01, correctif CI du 22 oct. 2026 : ce dialecte remplace une tentative precedente de contourner
 * le probleme en bornant ces colonnes en {@code VARCHAR(10485760)} cote entite et changelog, qui s'est
 * heurtee au seuil interne (non documente, dependant de la version Hibernate) a partir duquel Hibernate
 * bascule sa validation de schema de VARCHAR vers CLOB selon la longueur demandee. Cette approche-ci est
 * deterministe : CLOB des deux cotes (entite et changelog Liquibase), partout, sans seuil a deviner ;
 * seule la DDL PostgreSQL physique change ({@code text} au lieu d'{@code oid}).
 *
 * <p>Vit dans {@code infra-jpa} (SILO-200) : aucun type metier, utilisable par tous les silos qui en
 * dependent deja pour {@link SiloPhysicalNamingStrategy} (voir {@code InfraJpaIndependenceArchTest}).
 */
public class TextClobPostgreSQLDialect extends PostgreSQLDialect {

    @Override
    public String columnType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.CLOB) {
            return "text";
        }
        return super.columnType(sqlTypeCode);
    }
}
