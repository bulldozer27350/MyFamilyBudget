package com.moe.myfamilybudget.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthEntityMapper;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

/**
 * DB-1050 : le modele JPA autonome du domaine Patrimoine (additif, sans lien avec le hub) doit restituer
 * placements (avec historique), biens immobiliers et categories d'actifs a l'identique, dans l'ordre de
 * saisie, sans arrondir les taux.
 */
@DataJpaTest
@DisplayName("WealthJpaModelTest -- modele JPA Patrimoine autonome (DB-1050)")
class WealthJpaModelTest {

    @Autowired
    private WealthPlacementRepository placementRepository;

    @Autowired
    private WealthRealEstateRepository realEstateRepository;

    @Autowired
    private WealthCategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager em;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static PlacementHistoryEntryModel entry(String id, String date, String value) {
        return new PlacementHistoryEntryModel(id, date, bd(value), "releve " + id);
    }

    private static PlacementModel placement(String id, String label, PlacementHistoryEntryModel... history) {
        return new PlacementModel(id, label, "Assurance-vie", bd("12000.50"), "2026-09-01", bd("150.00"),
                "2026-01", "2030-12", bd("0.01000000"), bd("0.03251234"), bd("0.06000000"), Boolean.TRUE,
                "note " + id, 2, bd("5000.00"), bd("1000.00"), 3, "cat-1", List.of(history));
    }

    private static void assertSameContent(Object actual, Object expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private List<PlacementModel> savePlacementsAndReload(List<PlacementModel> models) {
        placementRepository.deleteAll();
        placementRepository.flush();
        placementRepository.saveAll(WealthEntityMapper.toPlacementEntities(models));
        flushAndClear();
        return WealthEntityMapper.toPlacementModels(placementRepository.findAllByOrderByPositionAsc());
    }

    @Test
    @DisplayName("Placements et historiques relus a l'identique, dans l'ordre, doublons d'identifiant toleres")
    void placementsRoundTrip() {
        List<PlacementModel> models = List.of(
                placement("pl-2", "PEA", entry("h-1", "2026-07-01", "11000.00"), entry("h-2", "2026-08-01", "11500.25")),
                placement("pl-1", "Livret A"),
                placement("pl-2", "Doublon"));

        List<PlacementModel> reloaded = savePlacementsAndReload(models);

        assertSameContent(reloaded, models);
        assertThat(reloaded).extracting(PlacementModel::label).containsExactly("PEA", "Livret A", "Doublon");
        assertThat(reloaded.get(0).history()).extracting(PlacementHistoryEntryModel::id).containsExactly("h-1", "h-2");
    }

    @Test
    @DisplayName("Taux des trois scenarios non arrondis")
    void placementRatesAreNotRounded() {
        PlacementModel reloaded = savePlacementsAndReload(List.of(placement("pl-1", "PEA"))).get(0);

        assertThat(reloaded.rateCorr()).isEqualByComparingTo("0.03251234");
        assertThat(reloaded.ratePess()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("Champs optionnels absents conserves a null, historique absent relu vide")
    void optionalPlacementFieldsStayNull() {
        PlacementModel sparse = new PlacementModel("pl-bare", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        PlacementModel reloaded = savePlacementsAndReload(List.of(sparse)).get(0);

        assertThat(reloaded.id()).isEqualTo("pl-bare");
        assertThat(reloaded.balance()).isNull();
        assertThat(reloaded.excludedFromRetirement()).isNull();
        assertThat(reloaded.sweepPriority()).isNull();
        assertThat(reloaded.categoryId()).isNull();
        assertThat(reloaded.history()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Remplacer le contenu retire les historiques orphelins ; supprimer en cascade")
    void replacingAndDeletingCascadesToHistory() {
        savePlacementsAndReload(List.of(placement("pl-1", "PEA", entry("h-1", "2026-07-01", "1.00"),
                entry("h-2", "2026-08-01", "2.00"))));

        List<PlacementModel> replacement = List.of(placement("pl-1", "PEA", entry("h-2", "2026-08-01", "2.50")));
        assertSameContent(savePlacementsAndReload(replacement), replacement);
        assertThat(em.getEntityManager().createQuery("select count(h) from WealthPlacementHistoryEntity h", Long.class)
                .getSingleResult()).isEqualTo(1L);

        placementRepository.deleteAll();
        flushAndClear();
        assertThat(placementRepository.count()).isZero();
        assertThat(em.getEntityManager().createQuery("select count(h) from WealthPlacementHistoryEntity h", Long.class)
                .getSingleResult()).isZero();
    }

    @Test
    @DisplayName("Biens immobiliers relus a l'identique, taux de croissance non arrondi")
    void realEstateRoundTrip() {
        List<RealEstateModel> models = List.of(
                new RealEstateModel("re-2", "Residence principale", "Maison", bd("350000.00"), 2025, bd("0.02512345"),
                        "note"),
                new RealEstateModel("re-1", "Studio", null, null, null, null, null));

        realEstateRepository.saveAll(WealthEntityMapper.toRealEstateEntities(models));
        flushAndClear();

        List<RealEstateModel> reloaded =
                WealthEntityMapper.toRealEstateModels(realEstateRepository.findAllByOrderByPositionAsc());
        assertSameContent(reloaded, models);
        assertThat(reloaded.get(0).annualGrowthRate()).isEqualByComparingTo("0.02512345");
        assertThat(reloaded.get(1).currentValue()).isNull();
    }

    @Test
    @DisplayName("Categories d'actifs relues a l'identique (icone emoji, couleur optionnelle), ordre conserve")
    void categoriesRoundTrip() {
        List<AssetCategoryModel> models = List.of(
                new AssetCategoryModel("ac-2", "\uD83D\uDCC1", "Epargne", "cash", "#ff8800"),
                new AssetCategoryModel("ac-1", "\uD83D\uDCC8", "Bourse", "invested", null));

        categoryRepository.saveAll(WealthEntityMapper.toCategoryEntities(models));
        flushAndClear();

        assertThat(WealthEntityMapper.toCategoryModels(categoryRepository.findAllByOrderByPositionAsc()))
                .isEqualTo(models);
    }

    @Test
    @DisplayName("Le mapper tolere null (element et liste)")
    void mapperToleratesNull() {
        assertThat(WealthEntityMapper.toEntity((PlacementModel) null, 0)).isNull();
        assertThat(WealthEntityMapper.toEntity((RealEstateModel) null, 0)).isNull();
        assertThat(WealthEntityMapper.toEntity((AssetCategoryModel) null, 0)).isNull();
        assertThat(WealthEntityMapper.toPlacementEntities(null)).isEmpty();
        assertThat(WealthEntityMapper.toRealEstateModels(null)).isEmpty();
        assertThat(WealthEntityMapper.toCategoryEntities(Arrays.asList((AssetCategoryModel) null))).isEmpty();
    }
}
