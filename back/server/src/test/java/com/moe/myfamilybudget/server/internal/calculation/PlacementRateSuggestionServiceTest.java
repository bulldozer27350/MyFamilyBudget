package com.moe.myfamilybudget.server.internal.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness.Status;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;
import com.moe.myfamilybudget.server.internal.marketdata.YieldCurveQuote;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel.Item;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel.Kind;

class PlacementRateSuggestionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);
    private static final List<AssetCategoryModel> CATEGORIES = List.of(
            new AssetCategoryModel("c1", "💶", "Livrets", "cash"),
            new AssetCategoryModel("c2", "🛡️", "Assurance-vie euros", "fondsEuros"),
            new AssetCategoryModel("c3", "📈", "PEA", "actions"),
            new AssetCategoryModel("c4", "🏦", "Obligations", "obligations"));

    private final PlacementRateSuggestionService service = new PlacementRateSuggestionService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PlacementModel placement(String label, String category) {
        return new PlacementModel("id-" + label, label, category, bd("10000"), "2026-09-01", bd("0"), null, null,
                bd("0.011"), bd("0.022"), bd("0.033"), false, "");
    }

    private static YieldCurveQuote curve() {
        return new YieldCurveQuote(LocalDate.of(2026, 9, 17), bd("0.031481938332"), bd("0.034875063501"),
                bd("0.033048290045"), bd("0.033049365222"), bd("0.034150346282"), bd("0.039938667167"));
    }

    private static MarketRatesView market(RegulatedRatesQuote regulated, Status status, YieldCurveQuote curve) {
        return new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"), regulated, status, LocalDate.of(2026, 8, 1),
                null, Status.UNAVAILABLE, true, curve, curve == null ? Status.UNAVAILABLE : Status.FRESH, null);
    }

    private static final RegulatedRatesQuote AUGUST = new RegulatedRatesQuote(YearMonth.of(2026, 8),
            bd("0.017"), bd("0.017"), bd("0.022"));

    private PlacementRateSuggestionsModel run(List<PlacementModel> placements, MarketRatesView market, String amplitude) {
        return service.compute(placements, CATEGORIES, market, amplitude == null ? null : bd(amplitude), TODAY);
    }

    private static void assertRate(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, bd(expected).compareTo(actual), "attendu " + expected + " mais " + actual);
    }

    @Test
    @DisplayName("Livret A : taux correct = taux en vigueur, pessimiste et optimiste = ± amplitude, prochaine révision citée")
    void livretA() {
        Item item = run(List.of(placement("Livret A", "Livrets")), market(AUGUST, Status.FRESH, null), "0.01")
                .suggestions().get(0);

        assertEquals(Kind.SUGGESTION, item.kind());
        assertEquals("Livret A", item.benchmark());
        assertRate("0.007", item.suggestedPess());
        assertRate("0.017", item.suggestedCorr());
        assertRate("0.027", item.suggestedOpti());
        assertNull(item.caveat());
        assertTrue(item.basis().contains("août 2026") && item.basis().contains("1 février 2027"), item.basis());
        assertRate("0.011", item.currentPess());
        assertRate("0.022", item.currentCorr());
        assertRate("0.033", item.currentOpti());
    }

    @Test
    @DisplayName("Le scénario pessimiste ne descend jamais sous le minimum légal de 0,5 %")
    void pessimisticFloor() {
        RegulatedRatesQuote low = new RegulatedRatesQuote(YearMonth.of(2026, 8), bd("0.006"), bd("0.006"), bd("0.010"));

        Item item = run(List.of(placement("Livret A", "Livrets")), market(low, Status.FRESH, null), "0.01").suggestions().get(0);

        assertRate("0.005", item.suggestedPess());
        assertRate("0.006", item.suggestedCorr());
    }

    @Test
    @DisplayName("LDDS et LEP utilisent chacun leur taux ; un LEP sans taux publié n'a pas de suggestion")
    void lddsAndLep() {
        MarketRatesView m = market(AUGUST, Status.FRESH, null);
        List<Item> items = run(List.of(placement("LDDS Marie", "Livrets"), placement("LEP", "Livrets")), m, "0.01").suggestions();

        assertEquals("LDDS", items.get(0).benchmark());
        assertRate("0.017", items.get(0).suggestedCorr());
        assertEquals("LEP", items.get(1).benchmark());
        assertRate("0.022", items.get(1).suggestedCorr());

        RegulatedRatesQuote noLep = new RegulatedRatesQuote(YearMonth.of(2026, 8), bd("0.017"), bd("0.017"), null);
        Item missing = run(List.of(placement("LEP", "Livrets")), market(noLep, Status.FRESH, null), "0.01").suggestions().get(0);
        assertEquals(Kind.NONE, missing.kind());
    }

    @Test
    @DisplayName("Reconnaissance des libellés : accents, tirets, catégorie ; faux amis écartés")
    void labelDetection() {
        assertEquals("LIVRET_A", PlacementRateSuggestionService.detectRegulated("Mon Livret-A", null).name());
        assertEquals("LIVRET_A", PlacementRateSuggestionService.detectRegulated("Epargne", "livret a").name());
        assertEquals("LDDS", PlacementRateSuggestionService.detectRegulated("Livret de développement durable", null).name());
        assertEquals("LDDS", PlacementRateSuggestionService.detectRegulated("LDD Paul", null).name());
        assertEquals("LEP", PlacementRateSuggestionService.detectRegulated("Livret d'épargne populaire", null).name());
        assertNull(PlacementRateSuggestionService.detectRegulated("Livret ancien", null));
        assertNull(PlacementRateSuggestionService.detectRegulated("Leplus", null));
        assertNull(PlacementRateSuggestionService.detectRegulated("PEL", null));
        assertNull(PlacementRateSuggestionService.detectRegulated(null, null));
    }

    @Test
    @DisplayName("Une donnée réglementée ancienne est suggérée avec une réserve explicite")
    void staleDataCarriesCaveat() {
        RegulatedRatesQuote march = new RegulatedRatesQuote(YearMonth.of(2026, 3), bd("0.015"), bd("0.015"), bd("0.025"));

        Item item = run(List.of(placement("Livret A", "Livrets")), market(march, Status.STALE, null), "0.01").suggestions().get(0);

        assertEquals(Kind.SUGGESTION, item.kind());
        assertTrue(item.caveat().contains("Donnée ancienne") && item.caveat().contains("1 août 2026"), item.caveat());
    }

    @Test
    @DisplayName("Fonds en euros et obligations : taux de repère à 10 ans en taux annuel effectif, aucun scénario")
    void referenceOnly() {
        MarketRatesView m = market(AUGUST, Status.FRESH, curve());
        List<Item> items = run(List.of(placement("AV euros", "Assurance-vie euros"), placement("Fonds oblig", "Obligations")), m, "0.01")
                .suggestions();

        for (Item item : items) {
            assertEquals(Kind.REFERENCE, item.kind());
            assertNull(item.suggestedCorr());
            assertEquals("Taux à 10 ans zone euro AAA", item.benchmark());
            // exp(0,034875063501) - 1
            assertRate("0.035490", item.referenceRate().setScale(6, java.math.RoundingMode.HALF_UP));
            assertTrue(item.basis().contains("17 septembre 2026"));
        }
    }

    @Test
    @DisplayName("Actions, catégorie inconnue et compte non réglementé : aucune suggestion")
    void noSuggestion() {
        MarketRatesView m = market(AUGUST, Status.FRESH, curve());
        List<Item> items = run(List.of(placement("ETF Monde", "PEA"), placement("Truc", "Inconnue"),
                placement("Compte sur livret banque", "Livrets")), m, "0.01").suggestions();

        assertEquals(Kind.NONE, items.get(0).kind());
        assertEquals(Kind.NONE, items.get(1).kind());
        assertEquals(Kind.NONE, items.get(2).kind());
        assertTrue(items.get(2).basis().contains("dépend de votre banque"));
    }

    @Test
    @DisplayName("Sans donnée de marché : aucune suggestion, message explicite")
    void noMarketData() {
        MarketRatesView empty = market(null, Status.UNAVAILABLE, null);
        List<Item> items = run(List.of(placement("Livret A", "Livrets"), placement("AV euros", "Assurance-vie euros")), empty, "0.01")
                .suggestions();

        assertEquals(Kind.NONE, items.get(0).kind());
        assertTrue(items.get(0).basis().contains("indisponible"));
        assertEquals(Kind.NONE, items.get(1).kind());
    }

    @Test
    @DisplayName("Amplitude : défaut 1 pt, zéro accepté, hors plage refusée")
    void amplitude() {
        MarketRatesView m = market(AUGUST, Status.FRESH, null);
        List<PlacementModel> one = List.of(placement("Livret A", "Livrets"));

        assertRate("0.01", run(one, m, null).amplitude());
        Item flat = run(one, m, "0").suggestions().get(0);
        assertRate("0.017", flat.suggestedPess());
        assertRate("0.017", flat.suggestedOpti());
        assertThrows(IllegalArgumentException.class, () -> run(one, m, "-0.001"));
        assertThrows(IllegalArgumentException.class, () -> run(one, m, "0.051"));
    }

    @Test
    @DisplayName("Les notes rappellent que l'écart pessimiste/optimiste est une convention et que rien n'est appliqué")
    void notesAreHonest() {
        List<String> notes = run(List.of(), market(AUGUST, Status.FRESH, null), "0.02").notes();

        assertTrue(notes.get(0).contains("convention") && notes.get(0).contains("2,00"), notes.get(0));
        assertTrue(notes.get(1).contains("Rien n'est appliqué"));
    }
}
