package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineWriter;

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
 *
 * <p>R-30 (SILO-215, lot B) : {@link PatrimoineWriter} (en pratique {@code JpaWealthStore}, via
 * {@code PatrimoinePersistenceAdapter}) reecrit la liste entiere a chaque mutation (lecture, puis
 * {@code deleteAll}/{@code saveAll}) sans transaction englobante ni verrou propre. Meme pattern que
 * {@link TresorerieCommandService} (R-20) : chaque ecriture s'execute donc dans une transaction du
 * {@link TransactionRunner}, apres prise du verrou du silo Patrimoine ({@link SiloMutationLock}).
 */
@Service
public class PatrimoineCommandService {

    private static final Set<MutationSilo> WEALTH_SILOS = EnumSet.of(MutationSilo.WEALTH);

    private final PatrimoineWriter patrimoineWriter;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public PatrimoineCommandService(PatrimoineWriter patrimoineWriter,
                                    SiloMutationLock siloMutationLock,
                                    TransactionRunner transactionRunner) {
        this.patrimoineWriter = patrimoineWriter;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        require(list, "La liste patrimoine");
        return transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            return patrimoineWriter.savePatrimoineRow(list, body);
        });
    }

    public void deletePatrimoineRow(PatrimoineList list, String id) {
        require(list, "La liste patrimoine");
        require(id, "L'identifiant de la ligne");
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            patrimoineWriter.deletePatrimoineRow(list, id);
            return null;
        });
    }

    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        require(placementId, "L'identifiant du placement");
        return transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            return patrimoineWriter.addPlacementHistoryEntry(placementId, body);
        });
    }

    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body) {
        require(placementId, "L'identifiant du placement");
        require(entryId, "L'identifiant du point d'historique");
        return transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            return patrimoineWriter.updatePlacementHistoryEntry(placementId, entryId, body);
        });
    }

    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        require(placementId, "L'identifiant du placement");
        require(entryId, "L'identifiant du point d'historique");
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            patrimoineWriter.deletePlacementHistoryEntry(placementId, entryId);
            return null;
        });
    }

    public void addAssetCategory(AssetCategoryModel category) {
        require(category, "La categorie d'actif");
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            patrimoineWriter.addAssetCategory(category);
            return null;
        });
    }

    public void updateAssetCategory(String id, AssetCategoryField field, Object value) {
        require(id, "L'identifiant de la categorie d'actif");
        require(field, "Le champ de la categorie d'actif");
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            patrimoineWriter.updateAssetCategory(id, field, value);
            return null;
        });
    }

    public void removeAssetCategory(String id) {
        require(id, "L'identifiant de la categorie d'actif");
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(WEALTH_SILOS);
            patrimoineWriter.removeAssetCategory(id);
            return null;
        });
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " est obligatoire");
        }
    }
}
