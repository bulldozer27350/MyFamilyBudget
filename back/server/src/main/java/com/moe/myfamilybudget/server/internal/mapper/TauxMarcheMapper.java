package com.moe.myfamilybudget.server.internal.mapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.CourbeTauxDto;
import com.moe.myfamilybudget.api.model.TauxCreditImmobilierDto;
import com.moe.myfamilybudget.api.model.TauxMarcheDto;
import com.moe.myfamilybudget.api.model.TauxReglementesDto;
import com.moe.myfamilybudget.server.internal.marketdata.MarketDataFreshness;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.MortgageRateQuote;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;
import com.moe.myfamilybudget.server.internal.marketdata.YieldCurveQuote;

/**
 * Conversion de la vue des données de marché vers les DTOs OpenAPI (tag TauxMarche).
 */
@Component
public class TauxMarcheMapper {

    static final String SOURCE_CDC = "Caisse des Dépôts (opendata.caissedesdepots.fr)";
    static final String SOURCE_BDF = "Banque de France (Webstat)";
    static final String SOURCE_BCE = "BCE (courbe des taux, emprunts d'État zone euro AAA)";
    static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    public TauxMarcheDto toDto(MarketRatesView view) {
        TauxMarcheDto dto = new TauxMarcheDto();
        dto.setFetchedAt(view.fetchedAt() != null ? view.fetchedAt().toString() : null);
        dto.setLastRefreshError(view.lastRefreshError());

        TauxReglementesDto regulated = new TauxReglementesDto();
        regulated.setSource(SOURCE_CDC);
        regulated.setStatus(view.regulatedStatus().name());
        regulated.setLastRevisionDate(view.lastRevisionDate().toString());
        RegulatedRatesQuote quote = view.regulatedRates();
        if (quote != null) {
            regulated.setAsOf(quote.asOf().toString());
            regulated.setLivretA(quote.livretA());
            regulated.setLdds(quote.ldds());
            regulated.setLep(quote.lep());
        }
        regulated.setMessage(regulatedMessage(view.regulatedStatus(), quote, view.lastRevisionDate()));
        dto.setReglementes(regulated);
        dto.setCreditImmobilier(toCreditDto(view));
        dto.setCourbeTaux(toCurveDto(view));
        return dto;
    }

    private TauxCreditImmobilierDto toCreditDto(MarketRatesView view) {
        TauxCreditImmobilierDto dto = new TauxCreditImmobilierDto();
        dto.setSource(SOURCE_BDF);
        MortgageRateQuote quote = view.mortgageRate();
        if (quote != null) {
            dto.setAsOf(quote.asOf().toString());
            dto.setRate(quote.rate());
        }
        dto.setStatus(view.mortgageConfigured() || quote != null ? view.mortgageStatus().name() : NOT_CONFIGURED);
        dto.setMessage(mortgageMessage(view.mortgageStatus(), quote, view.mortgageConfigured()));
        return dto;
    }

    private CourbeTauxDto toCurveDto(MarketRatesView view) {
        CourbeTauxDto dto = new CourbeTauxDto();
        dto.setSource(SOURCE_BCE);
        YieldCurveQuote curve = view.yieldCurve();
        if (curve != null) {
            dto.setAsOf(curve.asOf().toString());
            dto.setSpot2y(curve.spot2y());
            dto.setSpot10y(curve.spot10y());
            dto.setForward1y(curve.forward1y());
            dto.setForward2y(curve.forward2y());
            dto.setForward5y(curve.forward5y());
            dto.setForward10y(curve.forward10y());
        }
        dto.setStatus(view.yieldCurveStatus().name());
        dto.setMessage(curveMessage(view.yieldCurveStatus(), curve));
        return dto;
    }

    static String mortgageMessage(RegulatedRateFreshness.Status status, MortgageRateQuote quote, boolean configured) {
        if (quote == null) {
            return configured
                    ? "Aucune donnée disponible : la source n'a pas encore pu être interrogée."
                    : "Clé d'API Banque de France non configurée (variable MYFAMILYBUDGET_BDF_API_KEY) : taux de marché à saisir à la main.";
        }
        String month = quote.asOf().format(MONTH_FORMAT);
        if (status == RegulatedRateFreshness.Status.STALE) {
            return "Dernière donnée : " + month + ", ancienne de plus de "
                    + MarketDataFreshness.MORTGAGE_MAX_AGE_MONTHS + " mois — à vérifier.";
        }
        return "Taux moyen des nouveaux crédits à l'habitat hors renégociations (" + month + ").";
    }

    static String curveMessage(RegulatedRateFreshness.Status status, YieldCurveQuote curve) {
        if (curve == null) {
            return "Aucune donnée disponible : la source n'a pas encore pu être interrogée.";
        }
        String day = curve.asOf().format(DAY_FORMAT);
        if (status == RegulatedRateFreshness.Status.STALE) {
            return "Dernière courbe : " + day + ", ancienne de plus de "
                    + MarketDataFreshness.YIELD_CURVE_MAX_AGE_DAYS + " jours — à vérifier.";
        }
        return "Courbe BCE du " + day + " (taux en composition continue, à convertir en taux annuel effectif pour comparer à un livret).";
    }

    /** Message prêt à afficher, qui explique la fiabilité de la valeur suggérée. */
    static String regulatedMessage(RegulatedRateFreshness.Status status, RegulatedRatesQuote quote, LocalDate lastRevision) {
        if (status == RegulatedRateFreshness.Status.UNAVAILABLE || quote == null) {
            return "Aucune donnée disponible : la source n'a pas encore pu être interrogée.";
        }
        YearMonth asOf = quote.asOf();
        if (status == RegulatedRateFreshness.Status.STALE) {
            return "Dernière donnée publiée : " + asOf.format(MONTH_FORMAT)
                    + ", antérieure à la dernière révision du " + lastRevision.format(DAY_FORMAT)
                    + " — valeur possiblement obsolète, à vérifier.";
        }
        return "Taux réglementés à jour (données de " + asOf.format(MONTH_FORMAT) + ").";
    }
}
