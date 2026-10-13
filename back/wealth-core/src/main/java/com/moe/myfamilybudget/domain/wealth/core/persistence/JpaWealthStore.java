package com.moe.myfamilybudget.domain.wealth.core.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.wealth.model.WealthMutatedEvent;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineSnapshotWriter;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineWriter;

/**
 * Adaptateur JPA du silo Patrimoine (SILO-215, lot B / R-30) : implemente {@link PatrimoineReader},
 * {@link PatrimoineWriter} et {@link PatrimoineSnapshotWriter} directement sur les tables {@code wealth_*},
 * sans passer par le cache global ni par le {@code PersistenceManager}.
 *
 * <p>DA-14 : les virements relevent du silo Tresorerie, pas de Patrimoine. Ce module ({@code wealth-core})
 * ne depend que de {@code wealth-api} et {@code infra-jpa} (voir le commentaire du pom) : il ne peut donc
 * pas appeler {@code JpaTreasuryStore} lui-meme. {@link #getTransfers()} et le cas {@link PatrimoineList#TRANSFERS}
 * ne sont donc pas supportes ici ; c'est {@code PatrimoinePersistenceAdapter} (module {@code persistence}, qui
 * voit les deux silos) qui route les virements vers le silo Tresorerie et delegue tout le reste a ce store.
 */
@Component
public class JpaWealthStore implements PatrimoineReader, PatrimoineWriter, PatrimoineSnapshotWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JpaWealthStore.class);

    private final WealthPlacementRepository placementRepo;
    private final WealthRealEstateRepository realEstateRepo;
    private final WealthCategoryRepository categoryRepo;
    private final ApplicationEventPublisher eventPublisher;

    public JpaWealthStore(WealthPlacementRepository placementRepo,
                          WealthRealEstateRepository realEstateRepo,
                          WealthCategoryRepository categoryRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.placementRepo = placementRepo;
        this.realEstateRepo = realEstateRepo;
        this.categoryRepo = categoryRepo;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // PatrimoineReader
    // -------------------------------------------------------------------------

    @Override
    public List<PlacementModel> getPlacements() {
        return WealthEntityMapper.toPlacementModels(placementRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<RealEstateModel> getRealEstate() {
        return WealthEntityMapper.toRealEstateModels(realEstateRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<AssetCategoryModel> getAssetCategories() {
        return WealthEntityMapper.toCategoryModels(categoryRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<PatrimoineTransferModel> getTransfers() {
        throw new UnsupportedOperationException(
                "Les virements relevent du silo Tresorerie (DA-14) : utiliser PatrimoinePersistenceAdapter");
    }

    // -------------------------------------------------------------------------
    // PatrimoineWriter — savePatrimoineRow
    // -------------------------------------------------------------------------

    @Override
    public Map<String, Object> savePatrimoineRow(PatrimoineList list, Map<String, Object> body) {
        if (list == PatrimoineList.TRANSFERS) {
            throw new UnsupportedOperationException(
                    "Les virements relevent du silo Tresorerie (DA-14) : utiliser PatrimoinePersistenceAdapter");
        }

        String givenId = body != null && body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String uid = (givenId != null && !givenId.trim().isEmpty()) ? givenId
                : ("pat_" + UUID.randomUUID().toString().substring(0, 8));

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);

        if (list == PatrimoineList.PLACEMENTS) {
            String label = getString(body, "label", "Nouveau placement");
            String category = getString(body, "category", "Epargne");
            BigDecimal balance = getBigDecimal(body, "balance", BigDecimal.ZERO);
            String balanceDate = getString(body, "balanceDate", "2026-01-01");
            BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
            String monthlyFrom = getString(body, "monthlyFrom", "2026-01-01");
            String monthlyUntil = getString(body, "monthlyUntil", "2050-12-31");
            BigDecimal ratePess = getBigDecimal(body, "ratePess", BigDecimal.ZERO);
            BigDecimal rateCorr = getBigDecimal(body, "rateCorr", BigDecimal.ZERO);
            BigDecimal rateOpti = getBigDecimal(body, "rateOpti", BigDecimal.ZERO);
            Boolean excludedFromRetirement = body != null && body.containsKey("excludedFromRetirement")
                    ? Boolean.valueOf(String.valueOf(body.get("excludedFromRetirement"))) : false;
            String notes = getString(body, "notes", "");
            Integer sweepPriority = getInteger(body, "sweepPriority", null);
            BigDecimal sweepCap = getBigDecimal(body, "sweepCap", null);
            BigDecimal pauseTriggerBalance = getBigDecimal(body, "pauseTriggerBalance", null);
            Integer pausePriority = getInteger(body, "pausePriority", null);
            String categoryId = getString(body, "categoryId", "");

            // L'historique de valorisation (releves reels) n'est pas edite depuis ce formulaire
            // generique : on le preserve tel quel pour la ligne existante afin de ne pas l'ecraser
            // a chaque enregistrement du placement.
            List<PlacementHistoryEntryModel> existingHistory = getPlacements().stream()
                    .filter(p -> Objects.equals(p.id(), uid))
                    .findFirst()
                    .map(PlacementModel::getEffectiveHistory)
                    .orElse(List.of());

            PlacementModel model = new PlacementModel(uid, label, category, balance, balanceDate, monthly,
                    monthlyFrom, monthlyUntil, ratePess, rateCorr, rateOpti, excludedFromRetirement, notes,
                    sweepPriority, sweepCap, pauseTriggerBalance, pausePriority, categoryId, existingHistory);
            model = syncBalanceFromHistory(model);

            List<PlacementModel> models = new ArrayList<>();
            boolean found = false;
            for (PlacementModel p : getPlacements()) {
                if (Objects.equals(p.id(), uid)) {
                    models.add(model);
                    found = true;
                } else {
                    models.add(p);
                }
            }
            if (!found) {
                models.add(model);
            }
            rewritePlacements(models);

            resultRow.put("label", label);
            resultRow.put("category", category);
            resultRow.put("balance", balance);
            resultRow.put("balanceDate", balanceDate);
            resultRow.put("monthly", monthly);
            resultRow.put("monthlyFrom", monthlyFrom);
            resultRow.put("monthlyUntil", monthlyUntil);
            resultRow.put("ratePess", ratePess);
            resultRow.put("rateCorr", rateCorr);
            resultRow.put("rateOpti", rateOpti);
            resultRow.put("excludedFromRetirement", excludedFromRetirement);
            resultRow.put("notes", notes);
            resultRow.put("sweepPriority", sweepPriority);
            resultRow.put("sweepCap", sweepCap);
            resultRow.put("pauseTriggerBalance", pauseTriggerBalance);
            resultRow.put("pausePriority", pausePriority);
            resultRow.put("categoryId", categoryId);
        } else {
            String label = getString(body, "label", "Nouveau bien");
            String type = getString(body, "type", "Résidence Principale");
            BigDecimal currentValue = getBigDecimal(body, "currentValue", BigDecimal.ZERO);
            Integer valuationYear = getInteger(body, "valuationYear", 2026);
            BigDecimal annualGrowthRate = getBigDecimal(body, "annualGrowthRate", new BigDecimal("0.02"));
            String notes = getString(body, "notes", "");

            RealEstateModel model = new RealEstateModel(uid, label, type, currentValue, valuationYear,
                    annualGrowthRate, notes);

            List<RealEstateModel> models = new ArrayList<>();
            boolean found = false;
            for (RealEstateModel re : getRealEstate()) {
                if (Objects.equals(re.id(), uid)) {
                    models.add(model);
                    found = true;
                } else {
                    models.add(re);
                }
            }
            if (!found) {
                models.add(model);
            }
            rewriteRealEstate(models);

            resultRow.put("label", label);
            resultRow.put("type", type);
            resultRow.put("currentValue", currentValue);
            resultRow.put("valuationYear", valuationYear);
            resultRow.put("annualGrowthRate", annualGrowthRate);
            resultRow.put("notes", notes);
        }

        publishMutated("savePatrimoineRow:" + list.key());
        return resultRow;
    }

    // -------------------------------------------------------------------------
    // PatrimoineWriter — deletePatrimoineRow
    // -------------------------------------------------------------------------

    @Override
    public void deletePatrimoineRow(PatrimoineList list, String id) {
        if (list == PatrimoineList.TRANSFERS) {
            throw new UnsupportedOperationException(
                    "Les virements relevent du silo Tresorerie (DA-14) : utiliser PatrimoinePersistenceAdapter");
        }
        if (id == null) {
            return;
        }

        if (list == PatrimoineList.PLACEMENTS) {
            rewritePlacements(getPlacements().stream().filter(p -> !Objects.equals(p.id(), id)).toList());
        } else {
            rewriteRealEstate(getRealEstate().stream().filter(re -> !Objects.equals(re.id(), id)).toList());
        }

        publishMutated("deletePatrimoineRow:" + list.key());
    }

    // -------------------------------------------------------------------------
    // PatrimoineWriter — historique de valorisation des placements
    // -------------------------------------------------------------------------

    @Override
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        if (placementId == null || body == null) {
            return result;
        }

        String uid = "hist_" + UUID.randomUUID().toString().substring(0, 8);
        String date = getString(body, "date", LocalDate.now().toString());
        BigDecimal value = getBigDecimal(body, "value", BigDecimal.ZERO);
        String notes = getString(body, "notes", "");

        PlacementHistoryEntryModel entry = new PlacementHistoryEntryModel(uid, date, value, notes);
        result.put("id", uid);
        result.put("date", date);
        result.put("value", value);
        result.put("notes", notes);

        List<PlacementModel> models = getPlacements().stream()
                .map(p -> {
                    if (!Objects.equals(p.id(), placementId)) {
                        return p;
                    }
                    List<PlacementHistoryEntryModel> history = new ArrayList<>(p.getEffectiveHistory());
                    history.add(entry);
                    return withHistory(p, history);
                })
                .toList();
        rewritePlacements(models);

        publishMutated("addPlacementHistoryEntry");
        return result;
    }

    @Override
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId,
                                                            Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        if (placementId == null || entryId == null || body == null) {
            return result;
        }

        List<PlacementModel> models = getPlacements().stream()
                .map(p -> {
                    if (!Objects.equals(p.id(), placementId)) {
                        return p;
                    }
                    List<PlacementHistoryEntryModel> history = p.getEffectiveHistory().stream()
                            .map(h -> {
                                if (!Objects.equals(h.id(), entryId)) {
                                    return h;
                                }
                                String date = body.containsKey("date") ? getString(body, "date", h.date()) : h.date();
                                BigDecimal value = body.containsKey("value")
                                        ? getBigDecimal(body, "value", h.value()) : h.value();
                                String notes = body.containsKey("notes")
                                        ? getString(body, "notes", h.notes()) : h.notes();
                                return new PlacementHistoryEntryModel(h.id(), date, value, notes);
                            })
                            .toList();
                    return withHistory(p, history);
                })
                .toList();
        rewritePlacements(models);

        publishMutated("updatePlacementHistoryEntry");
        return result;
    }

    @Override
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        if (placementId == null || entryId == null) {
            return;
        }

        List<PlacementModel> models = getPlacements().stream()
                .map(p -> {
                    if (!Objects.equals(p.id(), placementId)) {
                        return p;
                    }
                    List<PlacementHistoryEntryModel> history = p.getEffectiveHistory().stream()
                            .filter(h -> !Objects.equals(h.id(), entryId))
                            .toList();
                    return withHistory(p, history);
                })
                .toList();
        rewritePlacements(models);

        publishMutated("deletePlacementHistoryEntry");
    }

    // -------------------------------------------------------------------------
    // PatrimoineWriter — categories d'actifs
    // -------------------------------------------------------------------------

    @Override
    public void addAssetCategory(AssetCategoryModel category) {
        if (category == null) {
            return;
        }
        List<AssetCategoryModel> list = new ArrayList<>(getAssetCategories());
        list.add(category);
        rewriteCategories(list);
        publishMutated("addAssetCategory");
    }

    @Override
    public void updateAssetCategory(String id, AssetCategoryField field, Object value) {
        if (id == null || field == null) {
            return;
        }
        List<AssetCategoryModel> list = getAssetCategories().stream()
                .map(c -> Objects.equals(c.id(), id) ? applyField(c, field, value) : c)
                .toList();
        rewriteCategories(list);
        publishMutated("updateAssetCategory");
    }

    @Override
    public void removeAssetCategory(String id) {
        if (id == null) {
            return;
        }
        rewriteCategories(getAssetCategories().stream().filter(c -> !Objects.equals(c.id(), id)).toList());
        publishMutated("removeAssetCategory");
    }

    // -------------------------------------------------------------------------
    // PatrimoineSnapshotWriter
    // -------------------------------------------------------------------------

    /** SILO-119/R-30 : les virements (parametre {@code transfers}) ne relevent pas de ce silo (DA-14). */
    @Override
    public void replace(List<PlacementModel> placements, List<RealEstateModel> realEstate,
                        List<PatrimoineTransferModel> transfers, List<AssetCategoryModel> assetCategories) {
        rewritePlacements(orEmpty(placements));
        rewriteRealEstate(orEmpty(realEstate));
        rewriteCategories(orEmpty(assetCategories));
        publishMutated("replace");
    }

    @Override
    public void reset() {
        rewritePlacements(List.of());
        rewriteRealEstate(List.of());
        rewriteCategories(List.of());
        publishMutated("reset");
    }

    // -------------------------------------------------------------------------
    // Private rewrite helpers
    // -------------------------------------------------------------------------

    private void rewritePlacements(List<PlacementModel> models) {
        placementRepo.deleteAll();
        placementRepo.flush();
        if (!models.isEmpty()) {
            placementRepo.saveAll(WealthEntityMapper.toPlacementEntities(models));
        }
    }

    private void rewriteRealEstate(List<RealEstateModel> models) {
        realEstateRepo.deleteAll();
        realEstateRepo.flush();
        if (!models.isEmpty()) {
            realEstateRepo.saveAll(WealthEntityMapper.toRealEstateEntities(models));
        }
    }

    private void rewriteCategories(List<AssetCategoryModel> models) {
        categoryRepo.deleteAll();
        categoryRepo.flush();
        if (!models.isEmpty()) {
            categoryRepo.saveAll(WealthEntityMapper.toCategoryEntities(models));
        }
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new WealthMutatedEvent(mutationKind));
    }

    /**
     * {@code PlacementModel} n'a pas de methode "withHistory" (pas d'autre besoin dans le code) :
     * reconstruit un placement identique avec un historique different, puis resynchronise le solde
     * de reference sur la derniere valeur connue (voir {@link #syncBalanceFromHistory}).
     */
    private static PlacementModel withHistory(PlacementModel p, List<PlacementHistoryEntryModel> history) {
        PlacementModel next = new PlacementModel(
                p.id(), p.label(), p.category(), p.balance(), p.balanceDate(),
                p.monthly(), p.monthlyFrom(), p.monthlyUntil(),
                p.ratePess(), p.rateCorr(), p.rateOpti(),
                p.excludedFromRetirement(), p.notes(),
                p.sweepPriority(), p.sweepCap(), p.pauseTriggerBalance(),
                p.pausePriority(), p.categoryId(), history
        );
        return syncBalanceFromHistory(next);
    }

    /**
     * Le solde de reference (balance/balanceDate) du placement reste la source utilisee par Tresorerie
     * et Vue d'ensemble ; on le resynchronise automatiquement sur la derniere valeur d'historique connue
     * a chaque mutation. Les dates ISO (yyyy-MM-dd) se comparent correctement en tant que chaines.
     */
    private static PlacementModel syncBalanceFromHistory(PlacementModel p) {
        List<PlacementHistoryEntryModel> history = p.getEffectiveHistory();
        PlacementHistoryEntryModel latest = history.stream()
                .filter(h -> h.date() != null)
                .max((a, b) -> a.date().compareTo(b.date()))
                .orElse(null);
        if (latest == null) {
            return p;
        }
        return new PlacementModel(
                p.id(), p.label(), p.category(), latest.getEffectiveValue(), latest.date(),
                p.monthly(), p.monthlyFrom(), p.monthlyUntil(),
                p.ratePess(), p.rateCorr(), p.rateOpti(),
                p.excludedFromRetirement(), p.notes(),
                p.sweepPriority(), p.sweepCap(), p.pauseTriggerBalance(),
                p.pausePriority(), p.categoryId(), history
        );
    }

    private static AssetCategoryModel applyField(AssetCategoryModel c, AssetCategoryField field, Object value) {
        return switch (field) {
            case ICON -> new AssetCategoryModel(c.id(), toStringOrEmpty(value), c.name(), c.bucket(), c.color());
            case NAME -> new AssetCategoryModel(c.id(), c.icon(), toStringOrEmpty(value), c.bucket(), c.color());
            case BUCKET -> new AssetCategoryModel(c.id(), c.icon(), c.name(), toStringOrEmpty(value), c.color());
            case COLOR -> new AssetCategoryModel(c.id(), c.icon(), c.name(), c.bucket(), toStringOrEmpty(value));
        };
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    // -------------------------------------------------------------------------
    // Value conversion helpers
    // -------------------------------------------------------------------------

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private static String toStringOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toBigDecimal(map.get(key), defaultValue);
    }

    private static Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toInteger(map.get(key), defaultValue);
    }

    private static BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim().replace(",", ".");
            if (text.isEmpty()) {
                return fallback;
            }
            return new BigDecimal(text);
        } catch (Exception e) {
            LOG.warn("Valeur numerique decimale illisible, repli utilise : '{}'", value);
            return fallback;
        }
    }

    private static Integer toInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (Exception e) {
            LOG.warn("Valeur entiere illisible, repli utilise : '{}'", value);
            return fallback;
        }
    }
}
