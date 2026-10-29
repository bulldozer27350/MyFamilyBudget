package com.moe.myfamilybudget.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.factory.LoanAdviceInputFactory;
import com.moe.myfamilybudget.application.factory.PlacementRateSuggestionInputFactory;
import com.moe.myfamilybudget.application.model.PlacementRateSuggestionsModel;
import com.moe.myfamilybudget.application.rate.PlacementRateSuggestionInput;
import com.moe.myfamilybudget.application.rate.PlacementRateSuggestionService;
import com.moe.myfamilybudget.application.settings.LoanAdviceSettingsPort;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersValuesModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceResultModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAssumptionsModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanItemModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanMarketUseCase;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanRenegotiationAdviceModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanRenegotiationVerdict;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanRepayVerdict;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanRepaymentAdviceModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionItemModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionKind;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionsModel;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.LoanItem;
import com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.RenegotiationAdvice;
import com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.RepaymentAdvice;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.market.calculation.MarketDataService;
import com.moe.myfamilybudget.domain.market.model.MarketRatesView;
import com.moe.myfamilybudget.domain.market.model.MortgageRateQuote;
import com.moe.myfamilybudget.domain.market.model.RegulatedRateFreshness;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage « Prêts et taux de marché » (R-86, SILO-317).
 *
 * <p>Ce cas d'usage est transverse Patrimoine+Marché (décision du 14/10/2026) : il orchestre
 * l'analyse des prêts ({@link LoanAdviceCalculationService}) et les suggestions de taux
 * ({@link PlacementRateSuggestionService}), sans {@code ResponseEntity} ni DTO. Le module
 * {@code web} (aujourd'hui {@code AnalysePretsServiceImpl} et {@code SuggestionsTauxServiceImpl})
 * convertit les résultats vers le monde HTTP.
 *
 * <p>La logique de résolution du taux de marché (paramètre de requête → saisie manuelle →
 * Banque de France) est portée par ce cas d'usage ; elle était auparavant dans
 * {@code AnalysePretsServiceImpl}.
 *
 * <p>L'accès aux hypothèses de l'analyse passe par {@link LoanAdviceSettingsPort}, port de
 * {@code application.settings} implémenté dans {@code server} jusqu'à R-02 (sortie des stores
 * de paramètres Crédit vers {@code credit-api}/{@code credit-core}, DA-13).
 */
@Service
public class DefaultLoanMarketUseCase implements LoanMarketUseCase {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final LoanAdviceCalculationService calculationService;
    private final LoanAdviceSettingsPort settingsPort;
    private final MarketDataService marketDataService;
    private final LoanReader loanReader;
    private final PatrimoineReader patrimoineReader;
    private final PlacementRateSuggestionService suggestionService;

    public DefaultLoanMarketUseCase(
            LoanAdviceCalculationService calculationService,
            LoanAdviceSettingsPort settingsPort,
            MarketDataService marketDataService,
            LoanReader loanReader,
            PatrimoineReader patrimoineReader,
            PlacementRateSuggestionService suggestionService) {
        this.calculationService = calculationService;
        this.settingsPort = settingsPort;
        this.marketDataService = marketDataService;
        this.loanReader = loanReader;
        this.patrimoineReader = patrimoineReader;
        this.suggestionService = suggestionService;
    }

    @Override
    public LoanAdviceResultModel getAnalysePrets(BigDecimal marketRate) {
        LoanAdviceParameters saved = settingsPort.current();
        ResolvedMarketRate resolved = resolveMarketRate(marketRate, saved, marketDataService.current());
        LoanAdviceParameters effective = withMarketRate(saved, resolved.rate());
        com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel result =
                calculationService.compute(
                        LoanAdviceInputFactory.from(loanReader.getLoans(), patrimoineReader.getPlacements(),
                                patrimoineReader.getAssetCategories(), effective, resolved.rate(), LocalDate.now()));
        // Le calcul ignore un taux hors plage : l'origine n'est alors pas affichée.
        com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel described =
                result.marketRateUsed() != null
                        ? result.withMarketRateSource(resolved.source())
                        : result;
        return toModel(described);
    }

    @Override
    public LoanAdviceParametersModel getAnalysePretsParametres() {
        return toParametresModel(settingsPort.current(), settingsPort.defaults());
    }

    @Override
    public LoanAdviceParametersModel updateAnalysePretsParametres(LoanAdviceParametersSaveCommand command) {
        LoanAdviceParameters parameters = toParameters(command);
        settingsPort.save(parameters);
        return toParametresModel(parameters, settingsPort.defaults());
    }

    @Override
    public RateSuggestionsModel getSuggestionsTaux(BigDecimal amplitude) {
        PlacementRateSuggestionInput input = PlacementRateSuggestionInputFactory.from(
                patrimoineReader.getPlacements(),
                patrimoineReader.getAssetCategories(),
                marketDataService.current(),
                amplitude,
                LocalDate.now());
        PlacementRateSuggestionsModel model = suggestionService.compute(input);
        return toRateSuggestionsModel(model);
    }

    // ----------------------------------------------------------------------- traduction vers application-api

    private LoanAdviceResultModel toModel(com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel domain) {
        LoanAssumptionsModel assumptions = new LoanAssumptionsModel(
                domain.assumptions().loanType(),
                domain.assumptions().repayMarginRate(),
                domain.assumptions().renegotiationMinGapRate(),
                domain.assumptions().renegotiationMinCrd(),
                domain.assumptions().renegotiationMinRemainingMonths(),
                domain.assumptions().renegotiationFixedCosts(),
                domain.assumptions().flatTaxRate());
        List<LoanItemModel> loans = domain.loans().stream().map(this::toItemModel).toList();
        return new LoanAdviceResultModel(domain.marketRateUsed(), domain.marketRateSource(), assumptions, loans,
                List.copyOf(domain.notes()));
    }

    private LoanItemModel toItemModel(LoanItem item) {
        return new LoanItemModel(
                item.id(), item.label(), item.crd(), item.rate(), item.monthly(), item.insurance(),
                item.remainingMonths(), item.remainingInterest(), item.earlyRepaymentIndemnity(),
                toRepaymentModel(item.repayment()),
                toRenegotiationModel(item.renegotiation()));
    }

    private LoanRepaymentAdviceModel toRepaymentModel(RepaymentAdvice a) {
        LoanRepayVerdict verdict = LoanRepayVerdict.valueOf(a.verdict().name());
        return new LoanRepaymentAdviceModel(verdict, a.loanEffectiveCost(), a.alternativeNetYield(),
                a.alternativeLabel(), a.annualSaving(), a.indemnityPaybackMonths(), a.reason());
    }

    private LoanRenegotiationAdviceModel toRenegotiationModel(RenegotiationAdvice a) {
        LoanRenegotiationVerdict verdict = LoanRenegotiationVerdict.valueOf(a.verdict().name());
        return new LoanRenegotiationAdviceModel(verdict, a.gapRate(), a.newMonthly(), a.monthlyGain(),
                a.grossSaving(), a.costs(), a.netSaving(), a.paybackMonths(), a.reason());
    }

    private LoanAdviceParametersModel toParametresModel(LoanAdviceParameters current, LoanAdviceParameters defaults) {
        return new LoanAdviceParametersModel(toValuesModel(current), toValuesModel(defaults));
    }

    private LoanAdviceParametersValuesModel toValuesModel(LoanAdviceParameters p) {
        return new LoanAdviceParametersValuesModel(
                p.marketRate(), p.repayMarginRate(), p.renegotiationMinGapRate(), p.renegotiationMinCrd(),
                p.renegotiationMinRemainingMonths(), p.renegotiationFixedCosts(), p.flatTaxRate());
    }

    private LoanAdviceParameters toParameters(LoanAdviceParametersSaveCommand command) {
        return new LoanAdviceParameters(
                command.marketRate(),
                command.repayMarginRate(),
                command.renegotiationMinGapRate(),
                command.renegotiationMinCrd(),
                command.renegotiationMinRemainingMonths(),
                command.renegotiationFixedCosts(),
                command.flatTaxRate());
    }

    private RateSuggestionsModel toRateSuggestionsModel(PlacementRateSuggestionsModel model) {
        List<RateSuggestionItemModel> items = model.suggestions().stream()
                .map(item -> new RateSuggestionItemModel(
                        item.placementId(), item.label(), item.bucket(),
                        RateSuggestionKind.valueOf(item.kind().name()),
                        item.benchmark(),
                        item.suggestedPess(), item.suggestedCorr(), item.suggestedOpti(),
                        item.referenceRate(),
                        item.currentPess(), item.currentCorr(), item.currentOpti(),
                        item.basis(), item.caveat()))
                .toList();
        return new RateSuggestionsModel(model.amplitude(), items, List.copyOf(model.notes()));
    }

    // ----------------------------------------------------------------------- résolution du taux de marché

    /** Taux de marché retenu et son origine, affichée avec les résultats. */
    public record ResolvedMarketRate(BigDecimal rate, String source) {}

    public static ResolvedMarketRate resolveMarketRate(BigDecimal requested, LoanAdviceParameters saved, MarketRatesView market) {
        if (requested != null) {
            return new ResolvedMarketRate(requested, "Paramètre de requête");
        }
        if (saved.marketRate() != null) {
            return new ResolvedMarketRate(saved.marketRate(), "Saisie manuelle");
        }
        MortgageRateQuote mortgage = market.mortgageRate();
        if (mortgage != null) {
            String stale = market.mortgageStatus() == RegulatedRateFreshness.Status.STALE ? ", donnée ancienne" : "";
            return new ResolvedMarketRate(mortgage.rate(),
                    "Banque de France, " + mortgage.asOf().format(MONTH_FORMAT) + stale);
        }
        return new ResolvedMarketRate(null, null);
    }

    private static LoanAdviceParameters withMarketRate(LoanAdviceParameters p, BigDecimal marketRate) {
        return new LoanAdviceParameters(marketRate, p.repayMarginRate(), p.renegotiationMinGapRate(),
                p.renegotiationMinCrd(), p.renegotiationMinRemainingMonths(), p.renegotiationFixedCosts(),
                p.flatTaxRate());
    }
}
