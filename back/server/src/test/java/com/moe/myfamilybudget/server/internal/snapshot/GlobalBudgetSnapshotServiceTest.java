package com.moe.myfamilybudget.server.internal.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;

/**
 * CLEAN-020 -- Opérations globales isolées dans {@link GlobalBudgetSnapshotService} : export, import
 * (y compris corps {@code null}) et reset. Base H2 dédiée.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:clean020;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@DisplayName("CLEAN-020 -- Snapshot global (export/import/reset)")
class GlobalBudgetSnapshotServiceTest {

    @Autowired
    private GlobalBudgetSnapshotService snapshotService;

    @Autowired
    private PersistenceManager persistenceManager;

    @Test
    @DisplayName("import(null) n'écrit rien et renvoie l'état courant")
    void importNullKeepsCurrentState() {
        snapshotService.reset();
        BudgetDataModel before = persistenceManager.getBudgetData();

        BudgetDataDto result = snapshotService.importSnapshot(null);

        assertThat(persistenceManager.getBudgetData()).isEqualTo(before);
        assertThat(result).isEqualTo(snapshotService.export());
    }

    @Test
    @DisplayName("reset() renvoie le même contenu que l'export suivant")
    void resetMatchesFollowingExport() {
        BudgetDataDto reset = snapshotService.reset();

        assertThat(reset).isNotNull();
        // reset() renvoie le modèle par défaut en mémoire (BigDecimal sans échelle, ex. 47100), alors que
        // export() relit la base (NUMERIC avec échelle, ex. 47100.00) : equals() de BigDecimal tient compte
        // de l'échelle, on compare donc les montants par valeur (compareTo).
        assertThat(snapshotService.export())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(reset);
    }

    @Test
    @DisplayName("export -> import -> export est stable (aller-retour du snapshot)")
    void exportImportRoundTripIsStable() {
        snapshotService.reset();
        BudgetDataDto exported = snapshotService.export();

        BudgetDataDto imported = snapshotService.importSnapshot(exported);

        assertThat(imported).isEqualTo(exported);
        assertThat(snapshotService.export()).isEqualTo(exported);
    }
}
