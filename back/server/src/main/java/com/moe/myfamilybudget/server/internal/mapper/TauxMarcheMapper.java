package com.moe.myfamilybudget.server.internal.mapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.TauxMarcheDto;
import com.moe.myfamilybudget.api.model.TauxReglementesDto;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;

/**
 * Conversion de la vue des données de marché vers les DTOs OpenAPI (tag TauxMarche).
 */
@Component
public class TauxMarcheMapper {

    static final String SOURCE_CDC = "Caisse des Dépôts (opendata.caissedesdepots.fr)";

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
        return dto;
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
