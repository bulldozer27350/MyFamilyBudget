package com.moe.myfamilybudget.server.internal.migration;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentEntity;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * Étape 2 du plan de migration du stockage de l'import bancaire (areas/bank-pointage-relational-migration.md,
 * côté projet) : copie unique des données existantes de {@code bank_import_document} (document JSON,
 * R-01-lot-b, dépôt myfamilybudget-domain-bank-pointage) vers le schéma relationnel créé à l'étape 1
 * (changeSets {@code bank-reloc-core-1} à {@code -10}, même dépôt).
 *
 * <p><strong>Ne modifie ni ne supprime {@code bank_import_document}</strong> : lecture seule sur la source,
 * qui reste la copie de secours jusqu'à l'étape 5 du plan. Exécutée une seule fois par Liquibase (changeSet
 * {@code bank-reloc-core-11}, cf. {@code bank-pointage-core.changelog.xml}) : comme tout changeSet
 * Liquibase, le mécanisme de suivi standard ({@code DATABASECHANGELOG}) garantit qu'elle ne se réexécute
 * pas sur une base déjà migrée. Sur une base neuve (aucune ligne dans {@code bank_import_document}), ne
 * fait rien.
 *
 * <p><strong>Garantie « zéro perte de données »</strong> : chaque insertion est suivie d'un recomptage des
 * lignes réellement présentes dans la table cible, comparé à la taille de la liste correspondante dans le
 * modèle source. Le moindre écart fait échouer le changeSet ({@link CustomChangeException}), ce qui annule
 * la transaction du changeSet (comportement par défaut de Liquibase, non modifié ici) et empêche le
 * démarrage de l'application sur une migration incomplète ou partielle.
 *
 * <p>Convention de typage (voir commentaire du changeSet {@code bank-reloc-core-1} dans le changelog) : le
 * modèle Java représente « non catégorisé » par une chaîne vide, jamais {@code null}
 * ({@link BankImportModel.BankTransactionModel} et apparentés, constructeurs compacts). Cette migration
 * convertit systématiquement les chaînes vides en {@code NULL} SQL sur les colonnes {@code category_id},
 * pour rester cohérent avec l'absence de contrainte de clé étrangère stricte vers {@code bank_category}
 * décidée à l'étape 1.
 */
public class BankImportRelationalMigration implements CustomTaskChange {

    private static final Logger LOG = LoggerFactory.getLogger(BankImportRelationalMigration.class);

    @Override
    public void execute(Database database) throws CustomChangeException {
        Connection connection = toJdbcConnection(database);
        try {
            String jsonData = readExistingDocument(connection);
            if (jsonData == null || jsonData.isBlank()) {
                LOG.info("bank_import_document absent ou vide : rien a migrer vers le schema relationnel.");
                return;
            }

            BankImportModel model = BankImportDocumentMapper.toModel(new BankImportDocumentEntity(jsonData));
            if (model == null) {
                LOG.info("bank_import_document sans contenu exploitable : rien a migrer.");
                return;
            }

            migrateColumnMapping(connection, model);
            migrateCategories(connection, model);
            migrateRules(connection, model);
            migrateTransactions(connection, model);
            migratePendingOperations(connection, model);
            migrateMatchings(connection, model);

            checkCount(connection, "bank_category", model.categories().size());
            checkCount(connection, "bank_rule", model.rules().size());
            checkCount(connection, "bank_transaction", model.transactions().size());
            checkCount(connection, "bank_pending_operation", model.pendingOperations().size());
            checkCount(connection, "bank_matching", model.matchings().size());
            checkCount(connection, "bank_transaction_split", model.transactions().stream()
                    .mapToInt(tx -> tx.splits().size()).sum());
            checkCount(connection, "bank_pending_operation_split", model.pendingOperations().stream()
                    .mapToInt(op -> op.splits().size()).sum());
            checkCount(connection, "bank_matching_link", model.matchings().stream()
                    .mapToInt(matching -> matching.links().size()).sum());
            checkCount(connection, "bank_matching_link_tx", model.matchings().stream()
                    .flatMap(matching -> matching.links().stream())
                    .mapToInt(link -> link.txIds().size()).sum());

            LOG.info("Migration relationnelle de l'import bancaire terminee : {} categories, {} regles, "
                            + "{} transactions, {} operations en attente, {} mois de rapprochement.",
                    model.categories().size(), model.rules().size(), model.transactions().size(),
                    model.pendingOperations().size(), model.matchings().size());
        } catch (SQLException e) {
            throw new CustomChangeException(
                    "Migration de l'import bancaire vers le schema relationnel impossible : aucune donnee "
                            + "n'a ete perdue (bank_import_document n'est pas modifiee par cette etape), "
                            + "mais la migration doit etre rejouee apres correction.", e);
        }
    }

    private String readExistingDocument(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT json_data FROM bank_import_document ORDER BY id ASC")) {
            if (!rs.next()) {
                return null;
            }
            String jsonData = rs.getString(1);
            if (rs.next()) {
                // bank_import_document est un singleton fonctionnel (findFirstByOrderByIdAsc cote
                // JpaBankStore) : plus d'une ligne signale une anomalie sur laquelle on prefere
                // s'arreter plutot que de choisir silencieusement laquelle migrer.
                throw new SQLException(
                        "bank_import_document contient plusieurs lignes : migration interrompue, "
                                + "intervention manuelle necessaire.");
            }
            return jsonData;
        }
    }

    private void migrateColumnMapping(Connection connection, BankImportModel model) throws SQLException {
        BankImportModel.BankColumnMappingModel mapping = model.columnMapping();
        String sql = "INSERT INTO bank_column_mapping "
                + "(id, delimiter, date_format, has_header, date_col, label_col, type_col, amount_col, "
                + "extra_label_cols_csv, credit_cols_csv, debit_cols_csv) "
                + "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mapping.delimiter());
            ps.setString(2, mapping.dateFormat());
            ps.setBoolean(3, Boolean.TRUE.equals(mapping.hasHeader()));
            setNullableInt(ps, 4, mapping.dateCol());
            setNullableInt(ps, 5, mapping.labelCol());
            setNullableInt(ps, 6, mapping.typeCol());
            setNullableInt(ps, 7, mapping.amountCol());
            ps.setString(8, csvOfInts(mapping.extraLabelCols()));
            ps.setString(9, csvOfInts(mapping.creditCols()));
            ps.setString(10, csvOfInts(mapping.debitCols()));
            ps.executeUpdate();
        }
    }

    private void migrateCategories(Connection connection, BankImportModel model) throws SQLException {
        String sql = "INSERT INTO bank_category (id, label, kind, compressible) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (BankImportModel.CategoryModel category : model.categories()) {
                ps.setString(1, category.id());
                ps.setString(2, category.label());
                ps.setString(3, category.kind());
                ps.setString(4, category.compressible());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void migrateRules(Connection connection, BankImportModel model) throws SQLException {
        String sql = "INSERT INTO bank_rule (id, match_text, category_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (BankImportModel.BankImportRuleModel rule : model.rules()) {
                ps.setString(1, rule.id());
                ps.setString(2, rule.matchText());
                setNullIfBlank(ps, 3, rule.categoryId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void migrateTransactions(Connection connection, BankImportModel model) throws SQLException {
        String txSql = "INSERT INTO bank_transaction (id, transaction_date, label, type, amount, category_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        String splitSql = "INSERT INTO bank_transaction_split (id, transaction_id, category_id, amount, label) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement txPs = connection.prepareStatement(txSql);
             PreparedStatement splitPs = connection.prepareStatement(splitSql)) {
            for (BankImportModel.BankTransactionModel tx : model.transactions()) {
                txPs.setString(1, tx.id());
                txPs.setString(2, tx.date());
                txPs.setString(3, tx.label());
                txPs.setString(4, tx.type());
                txPs.setBigDecimal(5, tx.amount());
                setNullIfBlank(txPs, 6, tx.categoryId());
                txPs.addBatch();

                for (BankImportModel.BankTransactionSplitModel split : tx.splits()) {
                    splitPs.setString(1, split.id());
                    splitPs.setString(2, tx.id());
                    setNullIfBlank(splitPs, 3, split.categoryId());
                    splitPs.setBigDecimal(4, split.amount());
                    splitPs.setString(5, split.label());
                    splitPs.addBatch();
                }
            }
            txPs.executeBatch();
            splitPs.executeBatch();
        }
    }

    private void migratePendingOperations(Connection connection, BankImportModel model) throws SQLException {
        String opSql = "INSERT INTO bank_pending_operation "
                + "(id, operation_date, expected_date, type, ref_number, label, amount, category_id, status, "
                + "linked_tx_id, cleared_date, notes, budget_line_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String splitSql = "INSERT INTO bank_pending_operation_split "
                + "(id, pending_operation_id, category_id, amount, label) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement opPs = connection.prepareStatement(opSql);
             PreparedStatement splitPs = connection.prepareStatement(splitSql)) {
            for (BankImportModel.PendingOperationModel op : model.pendingOperations()) {
                opPs.setString(1, op.id());
                opPs.setString(2, op.date());
                opPs.setString(3, op.expectedDate());
                opPs.setString(4, op.type());
                opPs.setString(5, op.refNumber());
                opPs.setString(6, op.label());
                opPs.setBigDecimal(7, op.amount());
                setNullIfBlank(opPs, 8, op.categoryId());
                opPs.setString(9, op.status());
                opPs.setString(10, op.linkedTxId());
                opPs.setString(11, op.clearedDate());
                opPs.setString(12, op.notes());
                opPs.setString(13, op.budgetLineId());
                opPs.addBatch();

                for (BankImportModel.BankTransactionSplitModel split : op.splits()) {
                    splitPs.setString(1, split.id());
                    splitPs.setString(2, op.id());
                    setNullIfBlank(splitPs, 3, split.categoryId());
                    splitPs.setBigDecimal(4, split.amount());
                    splitPs.setString(5, split.label());
                    splitPs.addBatch();
                }
            }
            opPs.executeBatch();
            splitPs.executeBatch();
        }
    }

    private void migrateMatchings(Connection connection, BankImportModel model) throws SQLException {
        String monthSql = "INSERT INTO bank_matching (month) VALUES (?)";
        String linkSql = "INSERT INTO bank_matching_link (month, budget_line_id) VALUES (?, ?)";
        String linkTxSql = "INSERT INTO bank_matching_link_tx (matching_link_id, tx_id) VALUES (?, ?)";
        try (PreparedStatement monthPs = connection.prepareStatement(monthSql);
             PreparedStatement linkPs = connection.prepareStatement(linkSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement linkTxPs = connection.prepareStatement(linkTxSql)) {
            for (BankImportModel.MatchingModel matching : model.matchings()) {
                monthPs.setString(1, matching.month());
                monthPs.executeUpdate();

                for (BankImportModel.MatchingLinkModel link : matching.links()) {
                    linkPs.setString(1, matching.month());
                    linkPs.setString(2, link.budgetLineId());
                    linkPs.executeUpdate();

                    long linkId;
                    try (ResultSet keys = linkPs.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Identifiant genere manquant pour bank_matching_link (mois " + matching.month() + ")");
                        }
                        linkId = keys.getLong(1);
                    }

                    for (String txId : link.txIds()) {
                        linkTxPs.setLong(1, linkId);
                        linkTxPs.setString(2, txId);
                        linkTxPs.addBatch();
                    }
                }
            }
            linkTxPs.executeBatch();
        }
    }

    private void checkCount(Connection connection, String tableName, int expected) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            int actual = rs.getInt(1);
            if (actual != expected) {
                throw new SQLException(String.format(
                        "Verification post-migration echouee sur %s : %d ligne(s) attendue(s), %d trouvee(s).",
                        tableName, expected, actual));
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullIfBlank(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static String csvOfInts(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private Connection toJdbcConnection(Database database) throws CustomChangeException {
        if (!(database.getConnection() instanceof JdbcConnection jdbcConnection)) {
            throw new CustomChangeException(
                    "Connexion non JDBC : la migration relationnelle de l'import bancaire requiert un acces JDBC direct.");
        }
        return jdbcConnection.getUnderlyingConnection();
    }

    @Override
    public String getConfirmationMessage() {
        return "Migration de l'import bancaire (bank_import_document) vers le schema relationnel effectuee.";
    }

    @Override
    public void setUp() throws SetupException {
        // Rien a preparer : toute l'etat necessaire est lu depuis la connexion JDBC fournie a execute().
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        // Aucune ressource externe (fichier) necessaire a cette migration.
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
