package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reproduit l'incident du mini-serveur : l'ancienne table {@code bank_import} (supprimee du modele JPA par
 * DB-1130) garde une cle etrangere vers {@code budget_data}, ce qui fait echouer le
 * {@code deleteAll()} de {@code BudgetPersistenceGateway.save()} et refuse toute ecriture (HTTP 500).
 */
class LegacySchemaCleanupTest {

    private JdbcDataSource dataSource;

    @BeforeEach
    void createLegacySchema() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:legacy-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE budget_data (id BIGINT PRIMARY KEY)");
            s.execute("CREATE TABLE bank_import (id BIGINT PRIMARY KEY, budget_data_id BIGINT, "
                    + "CONSTRAINT fk_legacy_bank_import FOREIGN KEY (budget_data_id) REFERENCES budget_data(id))");
            s.execute("INSERT INTO budget_data (id) VALUES (31)");
            s.execute("INSERT INTO bank_import (id, budget_data_id) VALUES (1, 31)");
        }
    }

    @Test
    @DisplayName("sans nettoyage, la suppression de budget_data est refusee par la cle etrangere legacy")
    void legacyForeignKeyBlocksDelete() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("DELETE FROM budget_data"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("apres nettoyage, budget_data peut etre supprimee et les lignes bank_import sont conservees")
    void cleanupUnblocksDeleteAndKeepsLegacyRows() throws SQLException {
        new LegacySchemaCleanup(dataSource).dropLegacyBankImportForeignKeys();

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM budget_data");
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM bank_import")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("le nettoyage est idempotent et sans effet sans table legacy")
    void cleanupIsIdempotent() throws SQLException {
        LegacySchemaCleanup cleanup = new LegacySchemaCleanup(dataSource);
        cleanup.dropLegacyBankImportForeignKeys();
        cleanup.dropLegacyBankImportForeignKeys();

        JdbcDataSource fresh = new JdbcDataSource();
        fresh.setURL("jdbc:h2:mem:fresh-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        fresh.setUser("sa");
        new LegacySchemaCleanup(fresh).dropLegacyBankImportForeignKeys();
    }
}
