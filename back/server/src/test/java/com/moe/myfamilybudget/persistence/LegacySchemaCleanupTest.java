package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reproduit l'incident du mini-serveur : d'anciennes tables ({@code bank_import}, {@code tax_bracket}...),
 * supprimees du modele JPA par DB-1100 a DB-1130, gardent une cle etrangere vers {@code budget_data}, ce qui
 * fait echouer le {@code deleteAll()} de {@code BudgetPersistenceGateway.save()} et refuse toute ecriture
 * (HTTP 500). La table {@code income} joue ici le role d'un enfant vivant du hub (cascade JPA).
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
            s.execute("CREATE TABLE income (id BIGINT PRIMARY KEY, budget_data_id BIGINT, "
                    + "CONSTRAINT fk_live_income FOREIGN KEY (budget_data_id) REFERENCES budget_data(id))");
            s.execute("CREATE TABLE bank_import (id BIGINT PRIMARY KEY, budget_data_id BIGINT, "
                    + "CONSTRAINT fk_legacy_bank_import FOREIGN KEY (budget_data_id) REFERENCES budget_data(id))");
            s.execute("CREATE TABLE tax_bracket (id BIGINT PRIMARY KEY, budget_data_id BIGINT, "
                    + "CONSTRAINT fk_legacy_tax_bracket FOREIGN KEY (budget_data_id) REFERENCES budget_data(id))");
            s.execute("INSERT INTO budget_data (id) VALUES (31)");
            s.execute("INSERT INTO bank_import (id, budget_data_id) VALUES (1, 31)");
            s.execute("INSERT INTO tax_bracket (id, budget_data_id) VALUES (1, 31)");
        }
    }

    private LegacySchemaCleanup cleanup() {
        return new LegacySchemaCleanup(dataSource, Set.of("income"));
    }

    @Test
    @DisplayName("sans nettoyage, la suppression de budget_data est refusee par les cles etrangeres legacy")
    void legacyForeignKeysBlockDelete() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("DELETE FROM budget_data"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("apres nettoyage, budget_data peut etre supprimee et les lignes legacy sont conservees")
    void cleanupUnblocksDeleteAndKeepsLegacyRows() throws SQLException {
        cleanup().dropLegacyBudgetDataForeignKeys();

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM budget_data");
            assertThat(count(s, "bank_import")).isEqualTo(1);
            assertThat(count(s, "tax_bracket")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("la cle etrangere d'un enfant vivant du hub est conservee")
    void cleanupKeepsLiveChildForeignKey() throws SQLException {
        cleanup().dropLegacyBudgetDataForeignKeys();

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO income (id, budget_data_id) VALUES (1, 31)");
            assertThatThrownBy(() -> s.execute("DELETE FROM budget_data"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("le nettoyage est idempotent et sans effet sur une base neuve")
    void cleanupIsIdempotent() throws SQLException {
        LegacySchemaCleanup cleanup = cleanup();
        cleanup.dropLegacyBudgetDataForeignKeys();
        cleanup.dropLegacyBudgetDataForeignKeys();

        JdbcDataSource fresh = new JdbcDataSource();
        fresh.setURL("jdbc:h2:mem:fresh-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        fresh.setUser("sa");
        new LegacySchemaCleanup(fresh, Set.of("income")).dropLegacyBudgetDataForeignKeys();
    }

    private static int count(Statement s, String table) throws SQLException {
        try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }
}
