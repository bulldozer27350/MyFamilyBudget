package com.moe.myfamilybudget.server.internal.command;

import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-030 -- Le command service Patrimoine valide la commande, delegue au port d'ecriture et laisse
 * l'erreur de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le
 * lecteur Patrimoine.
 */
@DisplayName("DB-030 -- PatrimoineCommandService")
class PatrimoineCommandServiceTest {

    private static final AssetCategoryModel CATEGORY = new AssetCategoryModel("cat_1", "icon1", "Immobilier",
            "immobilier", "#ff0000");

    private PatrimoineWriter writer;
    private PatrimoineCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(PatrimoineWriter.class);
        service = new PatrimoineCommandService(writer);
    }

    // --- succes : delegation au port ---

    @Test
    @DisplayName("succes : les lignes patrimoine sont transmises au port et le resultat est renvoye")
    void rowCommandsDelegate() {
        Map<String, Object> body = Map.of("id", "plc_1", "label", "PEA");
        Map<String, Object> saved = Map.of("id", "plc_1");
        when(writer.savePatrimoineRow(PatrimoineList.PLACEMENTS, body)).thenReturn(saved);

        assertThat(service.savePatrimoineRow(PatrimoineList.PLACEMENTS, body)).isSameAs(saved);
        service.deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");

        verify(writer).deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");
    }

    @Test
    @DisplayName("succes : un corps null reste accepte pour savePatrimoineRow (contrat historique)")
    void nullBodyIsAllowedForSave() {
        service.savePatrimoineRow(PatrimoineList.PLACEMENTS, null);

        verify(writer).savePatrimoineRow(PatrimoineList.PLACEMENTS, null);
    }

    @Test
    @DisplayName("succes : historique de placement et categories d'actifs sont delegues")
    void historyAndCategoryCommandsDelegate() {
        Map<String, Object> body = Map.of("value", new BigDecimal("100"));
        Map<String, Object> added = Map.of("id", "hist_1");
        when(writer.addPlacementHistoryEntry("plc_1", body)).thenReturn(added);

        assertThat(service.addPlacementHistoryEntry("plc_1", body)).isSameAs(added);
        service.updatePlacementHistoryEntry("plc_1", "hist_1", body);
        service.deletePlacementHistoryEntry("plc_1", "hist_1");
        service.addAssetCategory(CATEGORY);
        service.updateAssetCategory("cat_1", AssetCategoryField.NAME, "Immo");
        service.removeAssetCategory("cat_1");

        verify(writer).updatePlacementHistoryEntry("plc_1", "hist_1", body);
        verify(writer).deletePlacementHistoryEntry("plc_1", "hist_1");
        verify(writer).addAssetCategory(CATEGORY);
        verify(writer).updateAssetCategory("cat_1", AssetCategoryField.NAME, "Immo");
        verify(writer).removeAssetCategory("cat_1");
    }

    // --- DB-050 : enum interprete a la frontiere REST ---

    @Test
    @DisplayName("DB-050 : PatrimoineList.fromKey est insensible a la casse ; une liste inconnue ou null est refusee")
    void listFromKey() {
        assertThat(PatrimoineList.fromKey("placements")).isEqualTo(PatrimoineList.PLACEMENTS);
        assertThat(PatrimoineList.fromKey("TRANSFERS")).isEqualTo(PatrimoineList.TRANSFERS);
        assertThat(PatrimoineList.fromKey("realestate")).isEqualTo(PatrimoineList.REAL_ESTATE);
        assertThatThrownBy(() -> PatrimoineList.fromKey("inconnue")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatrimoineList.fromKey("loans")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatrimoineList.fromKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DB-050 : AssetCategoryField.find respecte la casse et ignore les champs inconnus")
    void categoryFieldFind() {
        assertThat(AssetCategoryField.find("icon")).contains(AssetCategoryField.ICON);
        assertThat(AssetCategoryField.find("bucket")).contains(AssetCategoryField.BUCKET);
        assertThat(AssetCategoryField.find("Name")).isEmpty();
        assertThat(AssetCategoryField.find(null)).isEmpty();
    }

    // --- validation ---

    @Test
    @DisplayName("validation : un identifiant ou une categorie null est refuse et rien n'est ecrit")
    void nullArgumentsAreRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.savePatrimoineRow(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deletePatrimoineRow(null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deletePatrimoineRow(PatrimoineList.PLACEMENTS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addPlacementHistoryEntry(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updatePlacementHistoryEntry("plc_1", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deletePlacementHistoryEntry(null, "hist_1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addAssetCategory(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateAssetCategory(null, AssetCategoryField.NAME, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateAssetCategory("cat_1", null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeAssetCategory(null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    // --- propagation d'erreur ---

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailuresArePropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        when(writer.savePatrimoineRow(any(PatrimoineList.class), any())).thenThrow(dbDown);
        doThrow(dbDown).when(writer).deletePatrimoineRow(any(PatrimoineList.class), anyString());
        doThrow(dbDown).when(writer).addAssetCategory(any());

        assertThatThrownBy(() -> service.savePatrimoineRow(PatrimoineList.PLACEMENTS, Map.of())).isSameAs(dbDown);
        assertThatThrownBy(() -> service.deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1")).isSameAs(dbDown);
        assertThatThrownBy(() -> service.addAssetCategory(CATEGORY)).isSameAs(dbDown);
    }

    // --- integration avec l'adaptateur ---

    @Test
    @DisplayName("integration adaptateur : placement, historique et categories relus par le lecteur")
    void adapterWritesAreReadBackByReader() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        PatrimoinePersistenceAdapter adapter = new PatrimoinePersistenceAdapter(persistenceManager);
        PatrimoineCommandService realService = new PatrimoineCommandService(adapter);

        realService.savePatrimoineRow(PatrimoineList.PLACEMENTS, Map.of("id", "plc_1", "label", "PEA", "category", "Actions"));
        assertThat(adapter.getPlacements()).extracting(PlacementModel::id).containsExactly("plc_1");

        Map<String, Object> entry = realService.addPlacementHistoryEntry("plc_1",
                Map.of("date", "2026-01-01", "value", new BigDecimal("9500")));
        String entryId = (String) entry.get("id");
        assertThat(adapter.getPlacements().get(0).history()).hasSize(1);

        realService.deletePlacementHistoryEntry("plc_1", entryId);
        assertThat(adapter.getPlacements().get(0).history()).isEmpty();

        realService.addAssetCategory(CATEGORY);
        assertThat(adapter.getAssetCategories()).containsExactly(CATEGORY);
        realService.removeAssetCategory("cat_1");
        assertThat(adapter.getAssetCategories()).isEmpty();

        realService.deletePatrimoineRow(PatrimoineList.PLACEMENTS, "plc_1");
        assertThat(adapter.getPlacements()).isEmpty();
    }
}
