package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.rate.PlacementRateSuggestionService;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceResultModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanMarketUseCase;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionsModel;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceSettingsService;
import com.moe.myfamilybudget.domain.credit.core.DefaultLoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.market.calculation.MarketDataService;
import com.moe.myfamilybudget.domain.market.model.MarketRatesView;
import com.moe.myfamilybudget.domain.market.model.MortgageRateQuote;
import com.moe.myfamilybudget.domain.market.model.RegulatedRateFreshness.Status;
import com.moe.myfamilybudget.domain.market.model.RegulatedRatesQuote;
import com.moe.myfamilybudget.domain.market.model.YieldCurveQuote;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryLoanStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;

/**
 * Tests unitaires de {@link DefaultLoanMarketUseCase} (R-86, SILO-317).
 */
class DefaultLoanMarketUseCaseTest {

    private InMemoryLoanStore loanStore;
    private InMemoryWealthStore wealthStore;
    private InMemoryLoanAdviceSettingsService settingsService;
    private StubMarketDataService marketDataService;
    private LoanMarketUseCase useCase;

    private static final class InMemoryLoanAdviceSettingsService implements LoanAdviceSettingsService {
        LoanAdviceParameters content;

        @Override
        public LoanAdviceParameters current() {
            return content != null ? content : defaults();
        }

        @Override
        public LoanAdviceParameters defaults() {
            return LoanAdviceParameters.defaults(null);
        }

        @Override
        public LoanAdviceParameters save(LoanAdviceParameters parameters) {
            content = parameters;
            return parameters;
        }
    }

    private static final class StubMarketDataService implements MarketDataService {
        MarketRatesView view;

        StubMarketDataService(MarketRatesView view) {
            this.view = view;
        }

        @Override
        public MarketRatesView current() {
            return view;
        }

        @Override
        public MarketRatesView refresh() {
            return view;
        }
    }

    @BeforeEach
    void setUp() {
        loanStore = new InMemoryLoanStore();
        wealthStore = new InMemoryWealthStore();
        settingsService = new InMemoryLoanAdviceSettingsService();

        MortgageRateQuote mortgage = new MortgageRateQuote(YearMonth.of(2026, 5), new BigDecimal("0.0321"), "MIR1.X");
        RegulatedRatesQuote regulated = new RegulatedRatesQuote(YearMonth.of(2026, 8), new BigDecimal("0.017"),
                new BigDecimal("0.017"), new BigDecimal("0.022"));
        YieldCurveQuote curve = new YieldCurveQuote(LocalDate.of(2026, 9, 17), new BigDecimal("0.031481938332"),
                new BigDecimal("0.034875063501"), new BigDecimal("0.033048290045"), new BigDecimal("0.033049365222"),
                new BigDecimal("0.034150346282"), new BigDecimal("0.039938667167"));
        MarketRatesView market = new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"), regulated, Status.FRESH,
                LocalDate.of(2026, 8, 1), mortgage, Status.FRESH, true, curve, Status.FRESH, null);
        marketDataService = new StubMarketDataService(market);

        useCase = new DefaultLoanMarketUseCase(
                new DefaultLoanAdviceCalculationService(),
                settingsService,
                marketDataService,
                loanStore,
                wealthStore,
                new PlacementRateSuggestionService());
    }

    @Test
    @DisplayName("getAnalysePrets compose l'analyse des prêts et résout le taux de marché")
    void getAnalysePrets_computesAdviceAndResolvesRate() {
        LoanModel loan = new LoanModel("l1", "Prêt Résidence", new BigDecimal("200000"), new BigDecimal("0.045"),
                new BigDecimal("1200"), new BigDecimal("40"), "2026-01-01", null);
        loanStore.replace(List.of(loan));

        PlacementModel livret = new PlacementModel("p1", "Livret A", "Livrets", new BigDecimal("20000"),
                "2026-09-01", BigDecimal.ZERO, null, null, new BigDecimal("0.01"), new BigDecimal("0.02"),
                new BigDecimal("0.03"), false, "");
        wealthStore.replace(List.of(livret), List.of(), List.of(),
                List.of(new AssetCategoryModel("c1", "💶", "Livrets", "cash")));

        LoanAdviceResultModel result = useCase.getAnalysePrets(null);

        assertNotNull(result);
        assertEquals(1, result.loans().size());
        assertEquals("Prêt Résidence", result.loans().get(0).label());
        assertEquals(0, new BigDecimal("0.0321").compareTo(result.marketRateUsed()));
        assertThat(result.marketRateSource()).contains("Banque de France");
    }

    @Test
    @DisplayName("getAnalysePretsParametres et updateAnalysePretsParametres gèrent les hypothèses")
    void loanAdviceParameters_readAndUpdate() {
        LoanAdviceParametersModel initial = useCase.getAnalysePretsParametres();
        assertNotNull(initial.defaults());
        assertNotNull(initial.values());

        LoanAdviceParametersSaveCommand command = new LoanAdviceParametersSaveCommand(
                new BigDecimal("0.029"),
                new BigDecimal("0.015"),
                new BigDecimal("0.008"),
                new BigDecimal("40000"),
                48,
                new BigDecimal("1500"),
                new BigDecimal("0.30"));

        LoanAdviceParametersModel updated = useCase.updateAnalysePretsParametres(command);
        assertEquals(0, new BigDecimal("0.029").compareTo(updated.values().marketRate()));
        assertEquals(48, updated.values().renegotiationMinRemainingMonths());
    }

    @Test
    @DisplayName("getSuggestionsTaux calcule les suggestions pour les livrets réglementés")
    void getSuggestionsTaux_computesRateSuggestions() {
        PlacementModel livret = new PlacementModel("p1", "Livret A", "Livrets", new BigDecimal("5000"),
                "2026-09-01", BigDecimal.ZERO, null, null, new BigDecimal("0.01"), new BigDecimal("0.02"),
                new BigDecimal("0.03"), false, "");
        wealthStore.replace(List.of(livret), List.of(), List.of(),
                List.of(new AssetCategoryModel("c1", "💶", "Livrets", "cash")));

        RateSuggestionsModel suggestions = useCase.getSuggestionsTaux(new BigDecimal("0.01"));

        assertNotNull(suggestions);
        assertEquals(1, suggestions.suggestions().size());
        assertEquals("Livret A", suggestions.suggestions().get(0).label());
        assertEquals("SUGGESTION", suggestions.suggestions().get(0).kind().name());
    }
}
