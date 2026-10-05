package com.moe.myfamilybudget.domain.market.core;

import com.moe.myfamilybudget.domain.market.calculation.MarketDataService;
import com.moe.myfamilybudget.domain.market.model.MarketDataFreshness;
import com.moe.myfamilybudget.domain.market.model.MarketRatesView;
import com.moe.myfamilybudget.domain.market.model.MarketSnapshot;
import com.moe.myfamilybudget.domain.market.model.MortgageRateQuote;
import com.moe.myfamilybudget.domain.market.model.RegulatedRateFreshness;
import com.moe.myfamilybudget.domain.market.model.RegulatedRatesQuote;
import com.moe.myfamilybudget.domain.market.model.YieldCurveQuote;
import com.moe.myfamilybudget.domain.market.port.MarketSnapshotStore;
import com.moe.myfamilybudget.domain.market.port.MortgageRateProvider;
import com.moe.myfamilybudget.domain.market.port.RegulatedRatesProvider;
import com.moe.myfamilybudget.domain.market.port.YieldCurveProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agrège les données de marché publiques et en garde le dernier instantané réussi (implémentation de
 * {@link MarketDataService}, SILO-159 ; bean déclaré par {@code DomainEngineConfig}).
 *
 * Règles de conception :
 * <ul>
 *   <li>chaque source est rafraîchie indépendamment : une source en échec conserve sa donnée
 *       précédente sans empêcher les autres de se mettre à jour ;</li>
 *   <li>une source non configurée (clé d'API absente) est ignorée sans erreur ;</li>
 *   <li>l'instantané est persisté, donc disponible après un redémarrage même sans réseau ;</li>
 *   <li>rien ici ne modifie les taux saisis par l'utilisateur : ces données ne sont que des
 *       suggestions.</li>
 * </ul>
 */
public class DefaultMarketDataService implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMarketDataService.class);
    private static final int MAX_ERROR_LENGTH = 300;

    private final RegulatedRatesProvider regulatedRatesProvider;
    private final MortgageRateProvider mortgageRateProvider;
    private final YieldCurveProvider yieldCurveProvider;
    private final MarketSnapshotStore store;
    private final Clock clock;

    private MarketSnapshot snapshot;
    private boolean loadedFromStore;
    private String lastRefreshError;

    public DefaultMarketDataService(RegulatedRatesProvider regulatedRatesProvider, MortgageRateProvider mortgageRateProvider,
            YieldCurveProvider yieldCurveProvider, MarketSnapshotStore store) {
        this(regulatedRatesProvider, mortgageRateProvider, yieldCurveProvider, store, Clock.systemDefaultZone());
    }

    DefaultMarketDataService(RegulatedRatesProvider regulatedRatesProvider, MortgageRateProvider mortgageRateProvider,
            YieldCurveProvider yieldCurveProvider, MarketSnapshotStore store, Clock clock) {
        this.regulatedRatesProvider = regulatedRatesProvider;
        this.mortgageRateProvider = mortgageRateProvider;
        this.yieldCurveProvider = yieldCurveProvider;
        this.store = store;
        this.clock = clock;
    }

    /** Vue courante, construite sans aucun appel réseau. */
    @Override
    public synchronized MarketRatesView current() {
        ensureLoaded();
        LocalDate today = LocalDate.now(clock);
        RegulatedRatesQuote regulated = snapshot != null ? snapshot.regulatedRates() : null;
        MortgageRateQuote mortgage = snapshot != null ? snapshot.mortgageRate() : null;
        YieldCurveQuote curve = snapshot != null ? snapshot.yieldCurve() : null;
        return new MarketRatesView(
                snapshot != null ? snapshot.fetchedAt() : null,
                regulated,
                RegulatedRateFreshness.evaluate(regulated != null ? regulated.asOf() : null, today),
                RegulatedRateFreshness.lastRevisionDate(today),
                mortgage,
                MarketDataFreshness.evaluateMortgageRate(mortgage != null ? mortgage.asOf() : null, today),
                mortgageRateProvider.isConfigured(),
                curve,
                MarketDataFreshness.evaluateYieldCurve(curve != null ? curve.asOf() : null, today),
                lastRefreshError);
    }

    /** Interroge chaque source, met à jour l'instantané si l'une d'elles a répondu, puis renvoie la vue. */
    @Override
    public synchronized MarketRatesView refresh() {
        ensureLoaded();
        RegulatedRatesQuote regulated = snapshot != null ? snapshot.regulatedRates() : null;
        MortgageRateQuote mortgage = snapshot != null ? snapshot.mortgageRate() : null;
        YieldCurveQuote curve = snapshot != null ? snapshot.yieldCurve() : null;
        List<String> errors = new ArrayList<>();

        Optional<RegulatedRatesQuote> newRegulated = fetch("Caisse des Dépôts", true, regulatedRatesProvider::fetchLatest, errors);
        Optional<MortgageRateQuote> newMortgage = fetch("Banque de France", mortgageRateProvider.isConfigured(),
                mortgageRateProvider::fetchLatest, errors);
        Optional<YieldCurveQuote> newCurve = fetch("BCE", yieldCurveProvider.isConfigured(),
                yieldCurveProvider::fetchLatest, errors);

        boolean changed = newRegulated.isPresent() || newMortgage.isPresent() || newCurve.isPresent();
        if (changed) {
            MarketSnapshot fresh = new MarketSnapshot(clock.instant(),
                    newRegulated.orElse(regulated), newMortgage.orElse(mortgage), newCurve.orElse(curve));
            store.save(fresh);
            snapshot = fresh;
        }
        lastRefreshError = errors.isEmpty() ? null : String.join(" ; ", errors);
        return current();
    }

    /**
     * Interroge une source en absorbant ses erreurs : un échec ou une réponse vide ajoute un message
     * à {@code errors} et renvoie vide. Une source non configurée est sautée sans message.
     */
    private <T> Optional<T> fetch(String sourceName, boolean configured, Supplier<Optional<T>> call, List<String> errors) {
        if (!configured) {
            return Optional.empty();
        }
        try {
            Optional<T> result = call.get();
            if (result.isEmpty()) {
                errors.add(sourceName + " : aucune donnée renvoyée");
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Rafraîchissement de la source {} en échec, dernière donnée conservée : {}", sourceName, e.getMessage());
            errors.add(sourceName + " : " + truncate(e.getMessage()));
            return Optional.empty();
        }
    }

    private void ensureLoaded() {
        if (!loadedFromStore) {
            snapshot = store.load().orElse(null);
            loadedFromStore = true;
        }
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "erreur inconnue lors de l'appel à la source";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
