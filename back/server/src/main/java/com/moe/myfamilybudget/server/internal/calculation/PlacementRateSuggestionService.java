package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;
import com.moe.myfamilybudget.server.internal.marketdata.YieldCurveQuote;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel.Item;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel.Kind;

/**
 * Suggestions de taux pessimiste / correct / optimiste pour les placements, à partir de données
 * publiques. Service sans état : les données de marché arrivent en paramètre.
 *
 * Périmètre volontairement étroit, limité à ce qui a une source défendable :
 * <ul>
 *   <li><b>Livret A, LDDS, LEP</b> (repérés par leur libellé) : le taux « correct » est le taux
 *       réglementé en vigueur, valable jusqu'à la prochaine révision ; les taux pessimiste et
 *       optimiste sont ce taux ∓ une <b>amplitude</b> (convention paramétrable, pas une prévision),
 *       le pessimiste ne descendant pas sous le minimum légal de 0,5 % ;</li>
 *   <li><b>fonds en euros, obligations</b> : aucun scénario, seulement un taux de repère (taux à
 *       10 ans des emprunts d'État de la zone euro AAA, converti en taux annuel effectif) ;</li>
 *   <li><b>autres placements</b> (actions, immobilier, épargne salariale, comptes non réglementés) :
 *       aucune suggestion, faute de source publique fiable de prévision.</li>
 * </ul>
 */
@Service
public class PlacementRateSuggestionService {

    public static final BigDecimal DEFAULT_AMPLITUDE = new BigDecimal("0.01");
    public static final BigDecimal MAX_AMPLITUDE = new BigDecimal("0.05");
    /** Taux minimal légal du Livret A et du LDDS ; appliqué comme plancher au scénario pessimiste. */
    private static final double REGULATED_FLOOR = 0.005;

    private static final Pattern LEP = Pattern.compile("(^|[^a-z0-9])lep([^a-z0-9]|$)|livret d'?epargne populaire");
    private static final Pattern LDDS = Pattern.compile("(^|[^a-z0-9])ldds?([^a-z0-9]|$)|livret de developpement durable");
    private static final Pattern LIVRET_A = Pattern.compile("livret[ -]?a([^a-z0-9]|$)");

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    enum Regulated {
        LIVRET_A("Livret A"),
        LDDS("LDDS"),
        LEP("LEP");

        final String label;

        Regulated(String label) {
            this.label = label;
        }
    }

    /**
     * @param amplitude écart pessimiste/optimiste en fraction (défaut 0,01), entre 0 et 0,05
     * @throws IllegalArgumentException si l'amplitude est hors plage (traduit en 400)
     */
    public PlacementRateSuggestionsModel compute(List<PlacementModel> placements, List<AssetCategoryModel> categories,
            MarketRatesView market, BigDecimal amplitude, LocalDate today) {
        BigDecimal amp = amplitude == null ? DEFAULT_AMPLITUDE : amplitude;
        if (amp.signum() < 0 || amp.compareTo(MAX_AMPLITUDE) > 0) {
            throw new IllegalArgumentException("L'amplitude pessimiste/optimiste doit être comprise entre 0 % et 5 %.");
        }
        AssetBucketResolver buckets = new AssetBucketResolver(categories);
        List<Item> items = new ArrayList<>();
        for (PlacementModel p : placements == null ? List.<PlacementModel>of() : placements) {
            items.add(suggestFor(p, buckets.bucketOf(p), market, amp.doubleValue(), today));
        }
        List<String> notes = List.of(
                "Aucune source publique ne publie de scénarios pessimiste et optimiste par placement : le taux « correct » "
                        + "vient de données de marché ou réglementaires, l'écart pessimiste/optimiste est une convention "
                        + "de ± " + String.format(Locale.FRENCH, "%.2f", amp.doubleValue() * 100) + " pt, pas une prévision.",
                "Rien n'est appliqué automatiquement : vous choisissez de reprendre ou non chaque suggestion.");
        return new PlacementRateSuggestionsModel(amp.setScale(6, RoundingMode.HALF_UP), items, notes);
    }

    private Item suggestFor(PlacementModel p, String bucket, MarketRatesView market, double amplitude, LocalDate today) {
        Regulated regulated = detectRegulated(p.label(), p.category());
        if (regulated != null) {
            return regulatedItem(p, bucket, regulated, market, amplitude, today);
        }
        if ("fondsEuros".equals(bucket)) {
            return referenceItem(p, bucket, market,
                    "Le rendement d'un fonds en euros est annoncé chaque année par l'assureur ; les fonds suivent les taux longs avec retard.");
        }
        if ("obligations".equals(bucket)) {
            return referenceItem(p, bucket, market,
                    "Repère pour un portefeuille obligataire de duration moyenne : le rendement réel dépend des titres détenus et du risque de crédit.");
        }
        String basis = "cash".equals(bucket)
                ? "Compte non réglementé : le taux dépend de votre banque, aucune source publique."
                : "Aucune source publique fiable de prévision de rendement pour cette classe d'actifs.";
        return none(p, bucket, basis);
    }

    private Item regulatedItem(PlacementModel p, String bucket, Regulated regulated, MarketRatesView market,
            double amplitude, LocalDate today) {
        RegulatedRatesQuote quote = market == null ? null : market.regulatedRates();
        BigDecimal rate = quote == null ? null : switch (regulated) {
            case LIVRET_A -> quote.livretA();
            case LDDS -> quote.ldds();
            case LEP -> quote.lep();
        };
        if (rate == null) {
            return none(p, bucket, "Taux du " + regulated.label + " indisponible : source non encore interrogée.");
        }
        double corr = rate.doubleValue();
        double pess = Math.min(corr, Math.max(REGULATED_FLOOR, corr - amplitude));
        double opti = corr + amplitude;
        String basis = "Taux du " + regulated.label + " publié par la Caisse des Dépôts (" + quote.asOf().format(MONTH_FORMAT)
                + "), supposé stable jusqu'à la prochaine révision du "
                + RegulatedRateFreshness.nextRevisionDate(today).format(DAY_FORMAT) + ".";
        String caveat = market.regulatedStatus() == RegulatedRateFreshness.Status.STALE
                ? "Donnée ancienne (antérieure à la dernière révision du " + market.lastRevisionDate().format(DAY_FORMAT)
                        + ") : vérifiez le taux en vigueur avant de l'appliquer."
                : null;
        return new Item(p.id(), p.label(), bucket, Kind.SUGGESTION, regulated.label,
                rate(pess), rate(corr), rate(opti), null,
                p.ratePess(), p.rateCorr(), p.rateOpti(), basis, caveat);
    }

    private Item referenceItem(PlacementModel p, String bucket, MarketRatesView market, String basisSuffix) {
        YieldCurveQuote curve = market == null ? null : market.yieldCurve();
        if (curve == null) {
            return none(p, bucket, "Courbe des taux indisponible : source non encore interrogée.");
        }
        double effective = Math.exp(curve.spot10y().doubleValue()) - 1;
        String basis = "Repère : taux à 10 ans des emprunts d'État de la zone euro notés AAA, courbe BCE du "
                + curve.asOf().format(DAY_FORMAT) + ", converti en taux annuel effectif. " + basisSuffix;
        return new Item(p.id(), p.label(), bucket, Kind.REFERENCE, "Taux à 10 ans zone euro AAA",
                null, null, null, rate(effective), p.ratePess(), p.rateCorr(), p.rateOpti(), basis, null);
    }

    private Item none(PlacementModel p, String bucket, String basis) {
        return new Item(p.id(), p.label(), bucket, Kind.NONE, null, null, null, null, null,
                p.ratePess(), p.rateCorr(), p.rateOpti(), basis, null);
    }

    /** Reconnaît un livret réglementé à son libellé ou à celui de sa catégorie (accents et casse ignorés). */
    static Regulated detectRegulated(String label, String category) {
        for (String text : new String[] {label, category}) {
            String n = normalize(text);
            if (LEP.matcher(n).find()) {
                return Regulated.LEP;
            }
            if (LDDS.matcher(n).find()) {
                return Regulated.LDDS;
            }
            if (LIVRET_A.matcher(n).find()) {
                return Regulated.LIVRET_A;
            }
        }
        return null;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static BigDecimal rate(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }
}
