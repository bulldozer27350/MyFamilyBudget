package com.moe.myfamilybudget.infra.jpa;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;

/**
 * Dialecte PostgreSQL qui mappe {@code @Lob String} sur {@code text}, pas {@code oid}, et qui lit/ecrit
 * cette colonne comme une chaine ordinaire plutot que par un flux « Large Object ».
 *
 * <p>Depuis Hibernate 5.6.2 (<a href="https://in.relation.to/2021/12/08/hibernate-orm-562/">note de
 * version</a>), {@code PostgreSQLDialect} mappe {@code Types.CLOB} sur {@code oid} par defaut (pour
 * permettre le streaming via Large Object), au lieu de {@code text} comme avant. Les silos de ce projet
 * n'ont jamais eu besoin de streaming sur leurs colonnes JSON ({@code json_data}, {@code payload_json}) :
 * le comportement {@code oid} exige une connexion non-autocommit pour la lecture et a deja cause un bug
 * (ancienne {@code BankImportEntity}, cf. Javadoc de {@code BankImportDocumentEntity} dans
 * {@code bank-pointage-core}). D'ou ce dialecte, qui restaure le comportement pre-5.6.2 pour {@code @Lob},
 * sans toucher au reste du dialecte PostgreSQL standard. Utilise uniquement en profil {@code docker}
 * (PostgreSQL de prod) ; H2 (profil par defaut, tests) n'a pas ce probleme, {@code CLOB} y reste
 * {@code CLOB} sans adaptation.
 *
 * <p>Deux changements, pas un seul :
 * <ul>
 *   <li>{@link #columnType(int)} : le DDL genere pour {@code CLOB} devient {@code text} au lieu
 *   d'{@code oid} (sans cette surcharge, Liquibase/Hibernate creeraient une colonne {@code oid}).</li>
 *   <li>{@link #contribute(TypeContributions, ServiceRegistry)} : enregistre {@link TextJdbcType} pour
 *   le code {@code CLOB}, afin que la lecture/ecriture passe par {@code getString}/{@code setString}
 *   plutot que par {@code getClob}/{@code setClob}. Sans ce second changement, le DDL est correct
 *   ({@code text}) mais Hibernate continue d'appeler {@code ResultSet#getClob()} a la lecture, et le
 *   driver PostgreSQL route cet appel par le gestionnaire de « Large Object » quelle que soit la nature
 *   physique de la colonne — d'ou l'echec observe en CI (22 oct. 2026, apres le premier correctif) :
 *   {@code PSQLException: Large Objects may not be used in auto-commit mode}, sur une simple lecture
 *   hors transaction explicite ({@code findFirstByOrderByIdAsc} de {@code JpaBankStore}, entre autres).
 *   Voir le Javadoc de {@link TextJdbcType} pour le detail du mecanisme.</li>
 * </ul>
 *
 * <p>R-01, correctif CI du 22 oct. 2026 (lot 1) : ce dialecte remplace une tentative precedente de
 * contourner le probleme en bornant ces colonnes en {@code VARCHAR(10485760)} cote entite et changelog,
 * qui s'est heurtee au seuil interne (non documente, dependant de la version Hibernate) a partir duquel
 * Hibernate bascule sa validation de schema de VARCHAR vers CLOB selon la longueur demandee. Lot 2 (meme
 * jour) : le lot 1 ne corrigeait que le DDL (ci-dessus) et pas le chemin de lecture/ecriture, d'ou
 * l'ajout de {@link TextJdbcType}. L'approche actuelle (lot 1 + lot 2) est deterministe : CLOB des deux
 * cotes (entite et changelog Liquibase), partout, sans seuil a deviner, et sans jamais solliciter le
 * mecanisme Large Object de PostgreSQL.
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

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contribute(typeContributions, serviceRegistry);
        typeContributions.getTypeConfiguration().getJdbcTypeRegistry()
                .addDescriptor(SqlTypes.CLOB, TextJdbcType.INSTANCE);
    }
}
