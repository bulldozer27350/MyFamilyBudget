package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agrège les données de marché publiques et en garde le dernier instantané réussi.
 *
 * Règles de conception :
 * <ul>
 *   <li>une source en échec ne fait jamais perdre l'instantané précédent : il est conservé et
 *       l'erreur est simplement exposée dans la vue ;</li>
 *   <li>l'instantané est persisté, donc disponible après un redémarrage même sans réseau ;</li>
 *   <li>rien ici ne modifie les taux saisis par l'utilisateur : ces données ne sont que des
 *       suggestions.</li>
 * </ul>
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final int MAX_ERROR_LENGTH = 300;

    private final RegulatedRatesProvider regulatedRatesProvider;
    private final MarketSnapshotStore store;
    private final Clock clock;

    private MarketSnapshot snapshot;
    private boolean loadedFromStore;
    private String lastRefreshError;

    @Autowired
    public MarketDataService(RegulatedRatesProvider regulatedRatesProvider, MarketSnapshotStore store) {
        this(regulatedRatesProvider, store, Clock.systemDefaultZone());
    }

    MarketDataService(RegulatedRatesProvider regulatedRatesProvider, MarketSnapshotStore store, Clock clock) {
        this.regulatedRatesProvider = regulatedRatesProvider;
        this.store = store;
        this.clock = clock;
    }

    /** Vue courante, construite sans aucun appel réseau. */
    public synchronized MarketRatesView current() {
        ensureLoaded();
        LocalDate today = LocalDate.now(clock);
        RegulatedRatesQuote quote = snapshot != null ? snapshot.regulatedRates() : null;
        return new MarketRatesView(
                snapshot != null ? snapshot.fetchedAt() : null,
                quote,
                RegulatedRateFreshness.evaluate(quote != null ? quote.asOf() : null, today),
                RegulatedRateFreshness.lastRevisionDate(today),
                lastRefreshError);
    }

    /** Interroge les sources, met à jour l'instantané en cas de succès, puis renvoie la vue. */
    public synchronized MarketRatesView refresh() {
        ensureLoaded();
        try {
            Optional<RegulatedRatesQuote> quote = regulatedRatesProvider.fetchLatest();
            if (quote.isPresent()) {
                MarketSnapshot fresh = new MarketSnapshot(clock.instant(), quote.get());
                store.save(fresh);
                snapshot = fresh;
                lastRefreshError = null;
            } else {
                lastRefreshError = "La source n'a renvoyé aucune donnée";
            }
        } catch (RuntimeException e) {
            log.warn("Rafraîchissement des taux de marché en échec, dernier instantané conservé : {}", e.getMessage());
            lastRefreshError = truncate(e.getMessage());
        }
        return current();
    }

    private void ensureLoaded() {
        if (!loadedFromStore) {
            snapshot = store.load().orElse(null);
            loadedFromStore = true;
        }
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Erreur inconnue lors de l'appel à la source";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
