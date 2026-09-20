package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.Assumptions;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.LoanItem;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RenegotiationAdvice;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RenegotiationVerdict;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RepayVerdict;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RepaymentAdvice;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

/**
 * Analyse des prêts en cours : faut-il les solder plus vite, les renégocier, ou les conserver ?
 *
 * Service sans état, sans accès à la persistance : toutes les données arrivent en paramètres, ce qui
 * le rend testable directement (même conception que OverviewCalculationService).
 *
 * Hypothèses structurantes (exposées dans le résultat et dans les notes) :
 * <ul>
 *   <li>prêts immobiliers : indemnité de remboursement anticipé (IRA) plafonnée au moindre de
 *       6 mois d'intérêts et 3 % du capital restant dû ;</li>
 *   <li>solder un prêt rapporte son coût de manière certaine : il n'est donc comparé qu'à des
 *       placements sans risque et liquides (buckets « cash » et « fondsEuros »), au taux « correct »,
 *       net de PFU hors livrets — pas aux actions, à l'immobilier ni à l'épargne retraite ;</li>
 *   <li>la renégociation est estimée à durée restante identique.</li>
 * </ul>
 * Calcul en double précision (estimation) ; les sorties sont arrondies (2 décimales pour les
 * montants, 6 pour les taux).
 */
@Service
public class LoanAdviceCalculationService {

    private static final int MAX_SIMULATION_MONTHS = 600;
    private static final double EPSILON = 0.005;
    private static final double IRA_CAP_RATE = 0.03;
    private static final String LOAN_TYPE = "immobilier";

    /** Meilleur placement liquide sans risque retenu comme alternative au remboursement. */
    private record Alternative(String label, double netYield) {
    }

    /** Échéancier restant d'un prêt. horizonKnown=false : le prêt ne s'amortit pas et n'a pas de date de fin. */
    private record Schedule(int months, double totalInterest, boolean horizonKnown) {
    }

    public LoanAdviceResultModel compute(
            List<LoanModel> loans,
            List<PlacementModel> placements,
            List<AssetCategoryModel> categories,
            LoanAdviceParameters params,
            LocalDate today) {

        List<String> notes = new ArrayList<>();
        BigDecimal market = params.marketRate();
        if (market != null && (market.signum() <= 0 || market.compareTo(LoanAdviceParameters.MAX_PLAUSIBLE_MARKET_RATE) > 0)) {
            notes.add("Taux de marché ignoré : valeur hors de la plage plausible (0 % à 30 %).");
            market = null;
        }

        Alternative alternative = bestLiquidAlternative(placements, categories, params);
        List<LoanItem> items = new ArrayList<>();
        for (LoanModel loan : loans == null ? List.<LoanModel>of() : loans) {
            double crd = projectCrd(loan, today);
            if (crd <= EPSILON) {
                continue;
            }
            items.add(analyseLoan(loan, crd, alternative, market, params, today));
        }

        notes.add("Estimation pour des prêts immobiliers : indemnité de remboursement anticipé plafonnée au moindre de "
                + "6 mois d'intérêts et 3 % du capital restant dû (à vérifier dans votre contrat).");
        notes.add("Le remboursement anticipé est comparé à un placement sans risque (livret, fonds en euros) au taux « correct » ; "
                + "plafonds de versement, épargne de précaution et disponibilité des fonds ne sont pas contrôlés.");
        if (market != null) {
            notes.add("Le taux de marché est un taux moyen : l'offre réelle d'une banque peut être meilleure ou moins bonne. "
                    + "La renégociation est estimée à durée restante identique, assurance et garantie non recalculées.");
        }

        Assumptions assumptions = new Assumptions(
                LOAN_TYPE,
                rate(params.repayMarginRate().doubleValue()),
                rate(params.renegotiationMinGapRate().doubleValue()),
                money(params.renegotiationMinCrd().doubleValue()),
                params.renegotiationMinRemainingMonths(),
                money(params.renegotiationFixedCosts().doubleValue()),
                rate(params.flatTaxRate().doubleValue()));
        return new LoanAdviceResultModel(market == null ? null : rate(market.doubleValue()), null, assumptions, items, notes);
    }

    // ------------------------------------------------------------------ prêt par prêt

    private LoanItem analyseLoan(LoanModel loan, double crd, Alternative alternative, BigDecimal market,
            LoanAdviceParameters params, LocalDate today) {
        double rate = value(loan.rate());
        double monthly = value(loan.monthly());
        double insurance = value(loan.insurance());
        double netPayment = Math.max(0, monthly - insurance);

        LocalDate end = parseDate(loan.endDate());
        boolean hasEnd = end != null;
        int cap = hasEnd ? Math.max(0, monthsUntil(today, end)) : MAX_SIMULATION_MONTHS;
        Schedule schedule = simulate(crd, rate, netPayment, cap, hasEnd);

        Integer remainingMonths = schedule.horizonKnown() ? schedule.months() : null;
        Double remainingInterest = schedule.horizonKnown() ? schedule.totalInterest() : null;
        double ira = crd * Math.min(rate / 2, IRA_CAP_RATE);

        RepaymentAdvice repayment = repaymentAdvice(crd, rate, insurance, remainingMonths, ira, alternative, params);
        RenegotiationAdvice renegotiation = renegotiationAdvice(crd, rate, netPayment, remainingMonths,
                remainingInterest, ira, market, params);

        return new LoanItem(
                loan.id(),
                loan.label(),
                money(crd),
                rate(rate),
                money(monthly),
                money(insurance),
                remainingMonths,
                remainingInterest == null ? null : money(remainingInterest),
                money(ira),
                repayment,
                renegotiation);
    }

    private RepaymentAdvice repaymentAdvice(double crd, double rate, double insurance, Integer remainingMonths,
            double ira, Alternative alternative, LoanAdviceParameters params) {
        double effectiveCost = rate + insurance * 12 / crd;
        if (alternative == null) {
            return new RepaymentAdvice(RepayVerdict.INCONNU, rate(effectiveCost), null, null, null, null,
                    "Aucun placement liquide (livret, fonds en euros) avec un taux renseigné : comparaison impossible.");
        }
        double diff = effectiveCost - alternative.netYield();
        double annualSaving = crd * diff;
        double margin = params.repayMarginRate().doubleValue();
        Integer payback = null;
        if (annualSaving > 0) {
            payback = ira <= 0 ? 0 : (int) Math.ceil(ira / (annualSaving / 12));
        }

        RepayVerdict verdict;
        String reason;
        if (diff > margin) {
            if (payback != null && remainingMonths != null && payback > remainingMonths) {
                verdict = RepayVerdict.NEUTRE;
                reason = "L'indemnité de remboursement anticipé (environ " + euros(ira)
                        + ") ne serait pas amortie avant la fin du prêt (" + remainingMonths + " mois restants).";
            } else {
                verdict = RepayVerdict.REMBOURSER;
                reason = "Le prêt coûte " + pct(effectiveCost) + " (assurance incluse) contre " + pct(alternative.netYield())
                        + " net pour « " + alternative.label() + " » : solder économiserait environ " + euros(annualSaving)
                        + " par an" + (payback != null ? ", indemnité amortie en " + payback + " mois." : ".");
            }
        } else if (diff < -margin) {
            verdict = RepayVerdict.CONSERVER;
            reason = "Le prêt coûte " + pct(effectiveCost) + " contre " + pct(alternative.netYield()) + " net pour « "
                    + alternative.label() + " » : conserver le prêt et garder l'épargne est plus avantageux.";
        } else {
            verdict = RepayVerdict.NEUTRE;
            reason = "Écart trop faible entre le coût du prêt (" + pct(effectiveCost) + ") et le rendement net de « "
                    + alternative.label() + " » (" + pct(alternative.netYield()) + ") pour trancher sur les seuls taux.";
        }
        return new RepaymentAdvice(verdict, rate(effectiveCost), rate(alternative.netYield()), alternative.label(),
                money(annualSaving), payback, reason);
    }

    private RenegotiationAdvice renegotiationAdvice(double crd, double rate, double netPayment, Integer remainingMonths,
            Double remainingInterest, double ira, BigDecimal market, LoanAdviceParameters params) {
        if (market == null) {
            return noRenegotiation(RenegotiationVerdict.INCONNU, null,
                    "Taux de marché non renseigné : renégociation non évaluable.");
        }
        double marketRate = market.doubleValue();
        double gap = rate - marketRate;
        if (gap < params.renegotiationMinGapRate().doubleValue()) {
            return noRenegotiation(RenegotiationVerdict.TAUX_PROCHE_MARCHE, gap,
                    "Votre taux (" + pct(rate) + ") est proche ou inférieur au taux de marché (" + pct(marketRate)
                            + ") : peu d'intérêt à renégocier.");
        }
        if (remainingMonths == null || remainingInterest == null) {
            return noRenegotiation(RenegotiationVerdict.NON_ELIGIBLE, gap,
                    "Durée restante indéterminée (mensualité insuffisante et pas de date de fin) : renégociation non évaluable.");
        }
        if (crd < params.renegotiationMinCrd().doubleValue()
                || remainingMonths < params.renegotiationMinRemainingMonths()) {
            return noRenegotiation(RenegotiationVerdict.NON_ELIGIBLE, gap,
                    "Capital restant dû (" + euros(crd) + ") ou durée restante (" + remainingMonths
                            + " mois) sous les seuils habituels (" + euros(params.renegotiationMinCrd().doubleValue()) + ", "
                            + params.renegotiationMinRemainingMonths() + " mois) : les frais absorbent l'économie.");
        }

        double newMonthly = annuity(crd, marketRate, remainingMonths);
        double newInterest = newMonthly * remainingMonths - crd;
        double grossSaving = remainingInterest - newInterest;
        double costs = ira + params.renegotiationFixedCosts().doubleValue();
        double netSaving = grossSaving - costs;
        double monthlyGain = netPayment - newMonthly;
        Integer payback = monthlyGain > 0 ? (int) Math.ceil(costs / monthlyGain) : null;

        RenegotiationVerdict verdict;
        String reason;
        if (netSaving > 0) {
            verdict = RenegotiationVerdict.RENEGOCIER;
            reason = "Au taux de marché (" + pct(marketRate) + ") : économie brute d'environ " + euros(grossSaving)
                    + ", " + euros(costs) + " de coûts (indemnité et frais estimés), soit " + euros(netSaving) + " net"
                    + (payback != null ? ", amortis en " + payback + " mois." : ".");
        } else {
            verdict = RenegotiationVerdict.NON_RENTABLE;
            reason = "Malgré un écart de " + pct(gap) + " avec le marché, l'économie brute (" + euros(grossSaving)
                    + ") ne couvre pas les coûts estimés (" + euros(costs) + ").";
        }
        return new RenegotiationAdvice(verdict, rate(gap), money(newMonthly), money(monthlyGain), money(grossSaving),
                money(costs), money(netSaving), payback, reason);
    }

    private static RenegotiationAdvice noRenegotiation(RenegotiationVerdict verdict, Double gap, String reason) {
        return new RenegotiationAdvice(verdict, gap == null ? null : rate(gap), null, null, null, null, null, null, reason);
    }

    // ------------------------------------------------------------------ alternative de placement

    private static Alternative bestLiquidAlternative(List<PlacementModel> placements,
            List<AssetCategoryModel> categories, LoanAdviceParameters params) {
        Map<String, String> bucketById = new HashMap<>();
        Map<String, String> bucketByName = new HashMap<>();
        for (AssetCategoryModel c : categories == null ? List.<AssetCategoryModel>of() : categories) {
            bucketById.put(c.id(), c.bucket());
            bucketByName.put(c.name(), c.bucket());
        }
        double flatTax = params.flatTaxRate().doubleValue();
        Alternative best = null;
        for (PlacementModel p : placements == null ? List.<PlacementModel>of() : placements) {
            String bucket = p.categoryId() != null ? bucketById.get(p.categoryId()) : null;
            if (bucket == null && p.category() != null) {
                bucket = bucketByName.get(p.category());
            }
            boolean cash = "cash".equals(bucket);
            if (!cash && !"fondsEuros".equals(bucket)) {
                continue;
            }
            double rate = value(p.rateCorr());
            if (rate <= 0) {
                continue;
            }
            double net = cash ? rate : rate * (1 - flatTax);
            if (best == null || net > best.netYield()) {
                best = new Alternative(p.label(), net);
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ amortissement

    /**
     * Capital restant dû à une date, projeté depuis la date de référence du prêt. Reproduit
     * projectLoanCrdToDate() de calculations.js (un pas par mois, du mois de référence au mois cible
     * inclus) pour que le serveur et l'affichage local restent cohérents.
     */
    static double projectCrd(LoanModel loan, LocalDate target) {
        double crd = value(loan.crd());
        if (crd <= 0) {
            return 0;
        }
        LocalDate start = parseDate(loan.startDate());
        if (start == null) {
            return round2(crd);
        }
        double rate = value(loan.rate());
        double netPayment = Math.max(0, value(loan.monthly()) - value(loan.insurance()));
        LocalDate end = parseDate(loan.endDate());

        int y = start.getYear();
        int m = start.getMonthValue() - 1;
        int targetAbs = target.getYear() * 12 + target.getMonthValue() - 1;
        while (crd > 0 && y * 12 + m <= targetAbs) {
            double monthlyInterest = crd * (rate / 12);
            double principalPaid = Math.min(crd, netPayment - monthlyInterest);
            crd = Math.max(0, crd - principalPaid);
            if (end != null && (y > end.getYear() || (y == end.getYear() && m >= end.getMonthValue() - 1))) {
                crd = 0;
            }
            m += 1;
            if (m > 11) {
                m = 0;
                y += 1;
            }
        }
        return round2(crd);
    }

    private static Schedule simulate(double crd, double rate, double netPayment, int maxMonths, boolean hasEnd) {
        double balance = crd;
        double interestSum = 0;
        int months = 0;
        while (balance > EPSILON && months < maxMonths) {
            double interest = balance * rate / 12;
            double principal = Math.min(balance, netPayment - interest);
            if (principal <= 0) {
                if (hasEnd) {
                    // Le prêt ne s'amortit pas mais une date de fin existe : intérêts seuls jusqu'à l'échéance.
                    interestSum += balance * rate / 12 * (maxMonths - months);
                    return new Schedule(maxMonths, interestSum, true);
                }
                return new Schedule(months, interestSum, false);
            }
            interestSum += interest;
            balance -= principal;
            months++;
        }
        return new Schedule(months, interestSum, balance <= EPSILON || hasEnd);
    }

    /** Mensualité d'un prêt à annuités constantes (hors assurance). */
    private static double annuity(double principal, double annualRate, int months) {
        double r = annualRate / 12;
        if (r == 0) {
            return principal / months;
        }
        return principal * r / (1 - Math.pow(1 + r, -months));
    }

    private static int monthsUntil(LocalDate from, LocalDate to) {
        return (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue());
    }

    static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return YearMonth.parse(text.trim()).atDay(1);
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private static double value(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static String pct(double fraction) {
        return String.format(Locale.FRENCH, "%.2f %%", fraction * 100);
    }

    private static String euros(double amount) {
        return String.format(Locale.FRENCH, "%,.0f €", amount);
    }
}
