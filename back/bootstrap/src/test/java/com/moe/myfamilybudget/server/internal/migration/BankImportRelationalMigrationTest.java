package com.moe.myfamilybudget.server.internal.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentEntity;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Preuve de non-regression / non-perte de donnees pour l'etape 2 du plan de migration
 * (areas/bank-pointage-relational-migration.md, cote projet) : {@link BankImportRelationalMigration}.
 *
 * <p>N'utilise volontairement pas de contexte Spring complet (contrairement a
 * {@code RestartPersistenceTest}) : seul le changelog du silo Banque/Pointage
 * ({@code db/changelog/bank-pointage-core/bank-pointage-core.changelog.xml}) est execute, via l'API Java
 * de Liquibase, directement sur une base H2 en memoire. Ca isole le scenario des autres silos (pas besoin
 * de faire demarrer tout le reste de l'application pour verifier une migration de donnees) et permet de
 * controler precisement QUAND les donnees existantes sont inserees par rapport a l'execution des
 * changeSets : c'est le point cle du test, et il n'est pas reproductible via deux demarrages normaux de
 * l'application (un changeSet Liquibase ne se rejoue jamais une fois applique).
 *
 * <p>Scenario, qui reproduit le cas reel d'un client existant qui installe cette mise a jour :
 * <ol>
 *   <li>on ne joue QUE les deux changeSets d'origine du silo (creation de {@code bank_import_document}
 *       et de {@code enable_banking_sync_state}, R-01-lot-b) -- l'etat d'une base avant ce patch ;</li>
 *   <li>on insere un document JSON representatif, serialise exactement comme le fait l'application
 *       aujourd'hui ({@link BankImportDocumentMapper}) ;</li>
 *   <li>on joue le reste du changelog (etape 1 : nouvelles tables : etape 2 : migration) ;</li>
 *   <li>on verifie que {@code bank_import_document} n'a pas bouge, et que chaque table relationnelle
 *       contient exactement les donnees attendues (comptages ET valeurs de champs, pas seulement les
 *       comptages deja verifies par la migration elle-meme).</li>
 * </ol>
 */
@DisplayName("Etape 2 -- Migration JSON vers relationnel de l'import bancaire")
class BankImportRelationalMigrationTest {

    private static final String CHANGELOG_PATH = "db/changelog/bank-pointage-core/bank-pointage-core.changelog.xml";

    @Test
    @DisplayName("Document JSON existant : migre integralement vers les tables relationnelles, sans alterer bank_import_document")
    void migratesExistingJsonDocumentIntoRelationalTables() throws Exception {
        try (Connection connection = newH2Connection()) {
            runChangeLogChangeSets(connection, 2); // R-01-lot-b-1 et -2 seulement : etat "avant ce patch"

            BankImportModel seed = representativeModel();
            String seedJson = BankImportDocumentMapper.toEntity(seed).getJsonData();
            insertSeedDocument(connection, seedJson);

            runChangeLogChangeSets(connection, -1); // le reste : nouvelles tables + migration

            // bank_import_document : toujours une seule ligne, contenu inchange (lecture seule garantie).
            assertThat(countRows(connection, "bank_import_document")).isEqualTo(1);
            assertThat(readSeedDocument(connection)).isEqualTo(seedJson);

            // Comptages (deja verifies en interne par la migration, mais on ne se fie pas qu'a elle-meme).
            assertThat(countRows(connection, "bank_category")).isEqualTo(1);
            assertThat(countRows(connection, "bank_rule")).isEqualTo(1);
            assertThat(countRows(connection, "bank_transaction")).isEqualTo(2);
            assertThat(countRows(connection, "bank_transaction_split")).isEqualTo(1);
            assertThat(countRows(connection, "bank_pending_operation")).isEqualTo(1);
            assertThat(countRows(connection, "bank_pending_operation_split")).isEqualTo(1);
            assertThat(countRows(connection, "bank_matching")).isEqualTo(1);
            assertThat(countRows(connection, "bank_matching_link")).isEqualTo(1);
            assertThat(countRows(connection, "bank_matching_link_tx")).isEqualTo(2);

            // Valeurs de champs : au moins un controle par table, pour ne pas se fier qu'aux comptages.
            assertThat(scalar(connection, "SELECT label FROM bank_category WHERE id = 'cat-courses'"))
                    .isEqualTo("Courses");
            assertThat(scalar(connection, "SELECT category_id FROM bank_rule WHERE id = 'rule-1'"))
                    .isEqualTo("cat-courses");
            assertThat(scalar(connection, "SELECT category_id FROM bank_transaction WHERE id = 'tx-non-categorisee'"))
                    .isNull(); // "" cote modele Java -> NULL en base, pas une chaine vide
            assertThat(scalar(connection, "SELECT amount FROM bank_transaction_split WHERE id = 'split-1'"))
                    .isEqualTo(new BigDecimal("12.50"));
            assertThat(scalar(connection, "SELECT category_id FROM bank_pending_operation_split WHERE id = 'pop-split-1'"))
                    .isNull(); // meme conversion "" -> NULL que pour une transaction, sur une repartition
            assertThat(countRows(connection, "SELECT * FROM bank_matching_link_tx WHERE tx_id = 'tx-categorisee'"))
                    .isEqualTo(1);

            // Config CSV (bank_column_mapping) : listes d'entiers -> CSV, et inversement a la lecture.
            assertThat(scalar(connection, "SELECT extra_label_cols_csv FROM bank_column_mapping WHERE id = 1"))
                    .isEqualTo("2,4");
        }
    }

    @Test
    @DisplayName("Aucun document existant (base neuve) : la migration ne fait rien, aucune table relationnelle n'est peuplee")
    void doesNothingWhenNoExistingDocument() throws Exception {
        try (Connection connection = newH2Connection()) {
            runChangeLogChangeSets(connection, -1); // changelog complet, bank_import_document reste vide

            assertThat(countRows(connection, "bank_import_document")).isZero();
            assertThat(countRows(connection, "bank_category")).isZero();
            assertThat(countRows(connection, "bank_transaction")).isZero();
            assertThat(countRows(connection, "bank_matching")).isZero();
        }
    }

    @Test
    @DisplayName("Plusieurs lignes dans bank_import_document (anomalie) : la migration s'arrete plutot que de choisir au hasard")
    void failsLoudlyWhenMultipleDocumentsExist() throws Exception {
        try (Connection connection = newH2Connection()) {
            runChangeLogChangeSets(connection, 2);
            insertSeedDocument(connection, BankImportDocumentMapper.toEntity(representativeModel()).getJsonData());
            insertSeedDocument(connection, BankImportDocumentMapper.toEntity(representativeModel()).getJsonData());

            assertThatThrownBy(() -> runChangeLogChangeSets(connection, -1))
                    .hasMessageContaining("plusieurs lignes");

            // Aucune table relationnelle ne doit avoir ete partiellement peuplee (changeSet transactionnel).
            assertThat(countRows(connection, "bank_category")).isZero();
        }
    }

    private BankImportModel representativeModel() {
        BankImportModel.BankColumnMappingModel mapping = new BankImportModel.BankColumnMappingModel(
                ";", "dd/MM/yyyy", true, 0, 1, 2, 3, List.of(2, 4), List.of(3), List.of());

        BankImportModel.CategoryModel category =
                new BankImportModel.CategoryModel("cat-courses", "Courses", "DEPENSE", "true");

        BankImportModel.BankImportRuleModel rule =
                new BankImportModel.BankImportRuleModel("rule-1", "CARREFOUR", "cat-courses");

        BankImportModel.BankTransactionSplitModel txSplit =
                new BankImportModel.BankTransactionSplitModel("split-1", "cat-courses", new BigDecimal("12.50"), "Part courses");
        BankImportModel.BankTransactionModel txCategorisee = new BankImportModel.BankTransactionModel(
                "tx-categorisee", "01/11/2026", "CARREFOUR MARKET", "DEBIT", new BigDecimal("42.90"),
                "cat-courses", List.of(txSplit));
        BankImportModel.BankTransactionModel txNonCategorisee = new BankImportModel.BankTransactionModel(
                "tx-non-categorisee", "02/11/2026", "VIREMENT RECU", "CREDIT", new BigDecimal("150.00"),
                null, List.of());

        BankImportModel.BankTransactionSplitModel pendingSplit =
                new BankImportModel.BankTransactionSplitModel("pop-split-1", null, new BigDecimal("5.00"), "Part attente");
        // Constructeur canonique a 14 parametres : (..., notes, splits, budgetLineId) -- l'ordre compte,
        // les deux derniers parametres sont de types differents (List puis String).
        BankImportModel.PendingOperationModel pendingOperation = new BankImportModel.PendingOperationModel(
                "pop-1", "03/11/2026", "05/11/2026", "CHEQUE", "CHQ001", "Cheque loyer", new BigDecimal("500.00"),
                "cat-courses", "EN_ATTENTE", null, null, "a pointer", List.of(pendingSplit), null);

        BankImportModel.MatchingLinkModel link =
                new BankImportModel.MatchingLinkModel("ligne-budget-1", List.of("tx-categorisee", "tx-non-categorisee"));
        BankImportModel.MatchingModel matching = new BankImportModel.MatchingModel("2026-11", List.of(link));

        return new BankImportModel(mapping, List.of(category), List.of(rule),
                List.of(txCategorisee, txNonCategorisee), List.of(pendingOperation), List.of(matching));
    }

    private Connection newH2Connection() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:bank_reloc_test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    /**
     * @param changesToApply nombre de changeSets en attente a appliquer (dans l'ordre du fichier), ou -1
     *                        pour appliquer tout ce qui reste.
     */
    private void runChangeLogChangeSets(Connection connection, int changesToApply) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(CHANGELOG_PATH, new ClassLoaderResourceAccessor(), database);
        if (changesToApply < 0) {
            liquibase.update(new Contexts());
        } else {
            liquibase.update(changesToApply, new Contexts());
        }
    }

    private void insertSeedDocument(Connection connection, String json) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO bank_import_document (json_data) VALUES (?)")) {
            ps.setString(1, json);
            ps.executeUpdate();
        }
    }

    private String readSeedDocument(Connection connection) throws Exception {
        return (String) scalar(connection, "SELECT json_data FROM bank_import_document");
    }

    private int countRows(Connection connection, String tableOrQuery) throws Exception {
        String sql = tableOrQuery.trim().toUpperCase().startsWith("SELECT")
                ? "SELECT COUNT(*) FROM (" + tableOrQuery + ") t"
                : "SELECT COUNT(*) FROM " + tableOrQuery;
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Object scalar(Connection connection, String sql) throws Exception {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            return rs.getObject(1);
        }
    }
}
