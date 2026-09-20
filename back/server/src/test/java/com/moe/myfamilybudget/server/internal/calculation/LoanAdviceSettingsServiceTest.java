package com.moe.myfamilybudget.server.internal.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoanAdviceSettingsServiceTest {

    /** Store en mémoire qui compte les enregistrements. */
    private static final class InMemoryStore implements LoanAdviceSettingsStore {
        LoanAdviceParameters content;
        int saves;

        @Override
        public Optional<LoanAdviceParameters> load() {
            return Optional.ofNullable(content);
        }

        @Override
        public void save(LoanAdviceParameters parameters) {
            content = parameters;
            saves++;
        }
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static LoanAdviceParameters custom(String market, String margin, String gap, String crd, int months,
            String costs, String tax) {
        return new LoanAdviceParameters(market == null ? null : bd(market), bd(margin), bd(gap), bd(crd), months,
                bd(costs), bd(tax));
    }

    @Test
    @DisplayName("Sans enregistrement, les valeurs par défaut validées avec l'utilisateur s'appliquent")
    void defaultsWhenNothingSaved() {
        LoanAdviceParameters p = new LoanAdviceSettingsService(new InMemoryStore()).current();

        assertNull(p.marketRate());
        assertEquals(0, bd("0.005").compareTo(p.repayMarginRate()));
        assertEquals(0, bd("0.007").compareTo(p.renegotiationMinGapRate()));
        assertEquals(0, bd("70000").compareTo(p.renegotiationMinCrd()));
        assertEquals(84, p.renegotiationMinRemainingMonths());
        assertEquals(0, bd("1500").compareTo(p.renegotiationFixedCosts()));
        assertEquals(0, bd("0.30").compareTo(p.flatTaxRate()));
    }

    @Test
    @DisplayName("Les hypothèses enregistrées remplacent les valeurs par défaut, qui restent disponibles")
    void savedValuesOverrideDefaults() {
        InMemoryStore store = new InMemoryStore();
        LoanAdviceSettingsService service = new LoanAdviceSettingsService(store);
        LoanAdviceParameters mine = custom("0.032", "0.01", "0.01", "50000", 60, "2000", "0.172");

        service.save(mine);

        assertEquals(1, store.saves);
        assertEquals(mine, service.current());
        assertEquals(0, bd("0.007").compareTo(service.defaults().renegotiationMinGapRate()));
        assertNull(service.defaults().marketRate());
    }

    @Test
    @DisplayName("Le taux de marché est facultatif et les bornes de chaque plage sont acceptées")
    void boundariesAreAccepted() {
        InMemoryStore store = new InMemoryStore();
        LoanAdviceSettingsService service = new LoanAdviceSettingsService(store);

        service.save(custom(null, "0", "0", "0", 0, "0", "0"));
        service.save(custom("0.30", "0.05", "0.05", "10000000", 600, "10000000", "1"));

        assertEquals(2, store.saves);
    }

    @Test
    @DisplayName("Une valeur hors plage est refusée avec un message explicite et rien n'est enregistré")
    void invalidValuesAreRejected() {
        InMemoryStore store = new InMemoryStore();
        LoanAdviceSettingsService service = new LoanAdviceSettingsService(store);

        LoanAdviceParameters[] invalid = {
                custom("0", "0.005", "0.007", "70000", 84, "1500", "0.3"),
                custom("0.31", "0.005", "0.007", "70000", 84, "1500", "0.3"),
                custom(null, "-0.001", "0.007", "70000", 84, "1500", "0.3"),
                custom(null, "0.06", "0.007", "70000", 84, "1500", "0.3"),
                custom(null, "0.005", "0.06", "70000", 84, "1500", "0.3"),
                custom(null, "0.005", "0.007", "-1", 84, "1500", "0.3"),
                custom(null, "0.005", "0.007", "70000", -1, "1500", "0.3"),
                custom(null, "0.005", "0.007", "70000", 601, "1500", "0.3"),
                custom(null, "0.005", "0.007", "70000", 84, "-1", "0.3"),
                custom(null, "0.005", "0.007", "70000", 84, "1500", "1.01"),
        };
        for (LoanAdviceParameters p : invalid) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.save(p));
            assertTrue(e.getMessage() != null && !e.getMessage().isBlank());
        }
        assertEquals(0, store.saves);
    }
}
