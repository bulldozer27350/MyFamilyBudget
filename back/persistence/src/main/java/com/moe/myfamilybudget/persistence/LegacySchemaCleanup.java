package com.moe.myfamilybudget.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Nettoyage de schema au demarrage : supprime les cles etrangeres de l'ancienne table {@code bank_import}
 * vers {@code budget_data}.
 *
 * <p>Depuis DB-1130, l'import bancaire n'est plus porte par le hub : l'entite {@code BankImportEntity} et sa
 * relation vers {@code BudgetDataEntity} ont ete supprimees (la table autonome {@code bank_import_document}
 * les remplace). Mais {@code spring.jpa.hibernate.ddl-auto=update} ne supprime jamais une table ni une
 * contrainte : sur une base existante (PostgreSQL du mini-serveur), la table {@code bank_import} et sa cle
 * etrangere survivent. Or {@code BudgetPersistenceGateway.save()} commence par
 * {@code budgetDataRepository.deleteAll()} : tant qu'une ligne {@code bank_import} reference {@code budget_data},
 * ce {@code DELETE} echoue (violation de contrainte) et TOUTE ecriture est refusee en HTTP 500.
 *
 * <p>Seule la contrainte est supprimee : la table et ses lignes restent en place (aucune donnee detruite,
 * retour arriere possible). Sans effet sur une base neuve (aucune table {@code bank_import}) et idempotent.
 * Declare avant {@link PersistenceManager} (voir {@code @DependsOn}) pour s'executer avant la premiere
 * lecture/ecriture du cache.
 */
@Component("legacySchemaCleanup")
public class LegacySchemaCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(LegacySchemaCleanup.class);

    private static final String PARENT_TABLE = "budget_data";
    private static final String LEGACY_TABLE = "bank_import";

    private final DataSource dataSource;

    public LegacySchemaCleanup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void dropLegacyBankImportForeignKeys() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            // PostgreSQL stocke les noms en minuscules, H2 en majuscules : on interroge les deux graphies.
            Set<String> spellings = new LinkedHashSet<>();
            spellings.add(PARENT_TABLE);
            spellings.add(PARENT_TABLE.toUpperCase());
            for (String spelling : spellings) {
                dropReferencingConstraints(connection, meta, catalog, schema, spelling);
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            // Ne bloque pas le demarrage, mais le signale : les ecritures resteront refusees tant que la
            // contrainte n'est pas supprimee (ALTER TABLE bank_import DROP CONSTRAINT <nom>).
            LOG.error("Nettoyage du schema legacy bank_import impossible", e);
        }
    }

    private void dropReferencingConstraints(Connection connection, DatabaseMetaData meta, String catalog,
                                            String schema, String parentTable) throws SQLException {
        Set<String> statements = new LinkedHashSet<>();
        try (ResultSet rs = meta.getExportedKeys(catalog, schema, parentTable)) {
            while (rs.next()) {
                String childTable = rs.getString("FKTABLE_NAME");
                String constraint = rs.getString("FK_NAME");
                String childSchema = rs.getString("FKTABLE_SCHEM");
                if (childTable == null || constraint == null || !LEGACY_TABLE.equalsIgnoreCase(childTable)) {
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

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
