package com.moe.myfamilybudget.persistence;

import java.lang.reflect.AnnotatedElement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.persistence.entity.BudgetDataEntity;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;

/**
 * Nettoyage de schema au demarrage : supprime les cles etrangeres <strong>obsoletes</strong> qui pointent
 * encore vers {@code budget_data} (anciennes tables {@code bank_import}, {@code tax_bracket}, {@code tax_child},
 * retraite...).
 *
 * <p>Les series DB-1100 a DB-1130 ont retire du hub {@code BudgetDataEntity} les relations Retraite, Fiscalite,
 * Banque... Mais {@code spring.jpa.hibernate.ddl-auto=update} ne supprime jamais une table ni une contrainte :
 * sur une base existante (PostgreSQL du mini-serveur), les anciennes tables et leurs cles etrangeres
 * survivent. Or {@code BudgetPersistenceGateway.save()} commence par {@code budgetDataRepository.deleteAll()} :
 * tant qu'une ligne d'une ancienne table reference {@code budget_data}, ce {@code DELETE} echoue (violation de
 * contrainte) et TOUTE ecriture est refusee en HTTP 500.
 *
 * <p>Regle : une cle etrangere vers {@code budget_data} est conservee uniquement si sa table est portee par une
 * entite qui declare encore une relation vers {@link BudgetDataEntity} dans le modele JPA courant (les enfants
 * vivants du hub, supprimes par cascade). Toutes les autres sont supprimees. Seules les contraintes sont
 * supprimees : les tables et leurs lignes restent en place (aucune donnee detruite). Sans effet sur une base
 * neuve, et idempotent. Declare avant {@link PersistenceManager} (voir {@code @DependsOn}) pour s'executer avant
 * la premiere lecture/ecriture du cache.
 */
@Component("legacySchemaCleanup")
public class LegacySchemaCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(LegacySchemaCleanup.class);

    private static final String PARENT_TABLE = "budget_data";

    private final DataSource dataSource;
    private final Set<String> liveChildTables;

    @Autowired
    public LegacySchemaCleanup(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this(dataSource, liveChildTablesOf(entityManagerFactory));
    }

    /** Visible pour les tests : liste explicite (minuscules) des tables enfants a conserver. */
    LegacySchemaCleanup(DataSource dataSource, Set<String> liveChildTables) {
        this.dataSource = dataSource;
        this.liveChildTables = lowerCased(liveChildTables);
    }

    @PostConstruct
    public void dropLegacyBudgetDataForeignKeys() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            // PostgreSQL stocke les noms en minuscules, H2 en majuscules : on interroge les deux graphies.
            Set<String> spellings = new LinkedHashSet<>();
            spellings.add(PARENT_TABLE);
            spellings.add(PARENT_TABLE.toUpperCase(Locale.ROOT));
            for (String spelling : spellings) {
                dropObsoleteConstraints(connection, meta, catalog, schema, spelling);
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            // Ne bloque pas le demarrage, mais le signale : les ecritures resteront refusees tant que les
            // contraintes obsoletes ne sont pas supprimees (ALTER TABLE <table> DROP CONSTRAINT <nom>).
            LOG.error("Nettoyage du schema legacy (cles etrangeres vers budget_data) impossible", e);
        }
    }

    private void dropObsoleteConstraints(Connection connection, DatabaseMetaData meta, String catalog,
                                         String schema, String parentTable) throws SQLException {
        Set<String> statements = new LinkedHashSet<>();
        try (ResultSet rs = meta.getExportedKeys(catalog, schema, parentTable)) {
            while (rs.next()) {
                String childTable = rs.getString("FKTABLE_NAME");
                String constraint = rs.getString("FK_NAME");
                String childSchema = rs.getString("FKTABLE_SCHEM");
                if (childTable == null || constraint == null
                        || liveChildTables.contains(childTable.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String qualified = (childSchema != null && !childSchema.isBlank())
                        ? quote(childSchema) + "." + quote(childTable)
                        : quote(childTable);
                statements.add("ALTER TABLE " + qualified + " DROP CONSTRAINT " + quote(constraint));
            }
        }
        for (String sql : statements) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
                LOG.warn("Schema legacy nettoye : {}", sql);
            }
        }
    }

    /** Tables des entites qui portent encore une relation vers {@link BudgetDataEntity} (enfants vivants du hub). */
    static Set<String> liveChildTablesOf(EntityManagerFactory entityManagerFactory) {
        Set<String> tables = new TreeSet<>();
        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            boolean referencesHub = false;
            for (Attribute<?, ?> attribute : entity.getAttributes()) {
                if (BudgetDataEntity.class.equals(attribute.getJavaType())) {
                    referencesHub = true;
                    break;
                }
            }
            if (referencesHub) {
                tables.add(tableNameOf(entity.getJavaType()));
            }
        }
        LOG.info("Tables enfants vivantes de budget_data (cles etrangeres conservees) : {}", tables);
        return tables;
    }

    private static String tableNameOf(AnnotatedElement entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        // Repli sur le nom de classe : toutes les entites du module declarent @Table(name = ...).
        return ((Class<?>) entityClass).getSimpleName();
    }

    private static Set<String> lowerCased(Set<String> names) {
        Set<String> result = new TreeSet<>();
        for (String name : names) {
            result.add(name.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
