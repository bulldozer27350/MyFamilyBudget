package com.moe.myfamilybudget.server.internal.command;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.port.AssetCategoryField;
import com.moe.myfamilybudget.server.internal.port.PatrimoineList;
import com.moe.myfamilybudget.server.internal.port.PatrimoineWriter;

/**
 * Service de commande du domaine Patrimoine (RF-A00, DB-030).
 * Unique point d'ecriture du patrimoine (immobilier, placements, categories d'actifs et historique de
 * valorisation) : valide la commande puis delegue au port {@link PatrimoineWriter}. N'a plus de
 * dependance directe vers {@code PersistenceManager}.
 *
 * <p>DB-050 : la liste est un enum ({@link PatrimoineList}) interprete a la frontiere REST ; ce service ne
 * manipule plus de {@code listKey} en chaine.
 *
 * <p>Les identifiants issus de l'URL ne sont jamais {@code null} cote REST : un {@code null} est donc une
 * erreur de programmation, refusee avant toute ecriture ({@link IllegalArgumentException}). Un corps
 * {@code null} reste accepte pour {@link #savePatrimoineRow} (creation d'une ligne par defaut, contrat
 * historique de l'API).
 */
@Service
public class PatrimoineCommandService {

    private final PatrimoineWriter patrimoineWriter;

    public PatrimoineCommandService(PatrimoineWriter patrimoineWriter) {
        this.patrimoineWriter = patrimoineWriter;
    }

    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        require(list, "La liste patrimoine");
        return patrimoineWriter.savePatrimoineRow(list, body);
    }

    public void deletePatrimoineRow(PatrimoineList list, String id) {
        require(list, "La liste patrimoine");
        require(id, "L'identifiant de la ligne");
        patrimoineWriter.deletePatrimoineRow(list, id);
    }

    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        require(placementId, "L'identifiant du placement");
        return patrimoineWriter.addPlacementHistoryEntry(placementId, body);
    }

    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body) {
        require(placementId, "L'identifiant du placement");
        require(entryId, "L'identifiant du point d'historique");
        return patrimoineWriter.updatePlacementHistoryEntry(placementId, entryId, body);
    }

    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        require(placementId, "L'identifiant du placement");
        require(entryId, "L'identifiant du point d'historique");
        patrimoineWriter.deletePlacementHistoryEntry(placementId, entryId);
    }

    public void addAssetCategory(AssetCategoryModel category) {
        require(category, "La categorie d'actif");
        patrimoineWriter.addAssetCategory(category);
    }

    public void updateAssetCategory(String id, AssetCategoryField field, Object value) {
        require(id, "L'identifiant de la categorie d'actif");
        require(field, "Le champ de la categorie d'actif");
        patrimoineWriter.updateAssetCategory(id, field, value);
    }

    public void removeAssetCategory(String id) {
        require(id, "L'identifiant de la categorie d'actif");
        patrimoineWriter.removeAssetCategory(id);
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " est obligatoire");
        }
    }
}
