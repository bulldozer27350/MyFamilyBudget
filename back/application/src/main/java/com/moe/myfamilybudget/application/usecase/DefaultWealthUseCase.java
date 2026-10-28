package com.moe.myfamilybudget.application.usecase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.factory.PatrimoineInputFactory;
import com.moe.myfamilybudget.application.usecase.wealth.AssetCategoryModel;
import com.moe.myfamilybudget.application.usecase.wealth.BankImportCategoryModel;
import com.moe.myfamilybudget.application.usecase.wealth.LoanModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineTransferModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineYearModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementEvolutionModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementModel;
import com.moe.myfamilybudget.application.usecase.wealth.WealthResultModel;
import com.moe.myfamilybudget.application.usecase.wealth.WealthUseCase;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolution;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage Patrimoine (R-81, même gabarit que {@code DefaultTaxUseCase},
 * SILO-312).
 *
 * <p>La logique de composition (anciennement {@code PatrimoineServiceImpl}) vit ici, sans
 * {@code ResponseEntity} ni DTO : elle lit ses fragments via les ports des silos
 * ({@link RetirementSettingsReader}, {@link TresorerieSettingsReader}, {@link EconomicAssumptionsReader},
 * {@link PatrimoineReader}, {@link BudgetReader}, {@link LoanReader}, {@link BankReader}), construit
 * l'entrée du moteur par {@link PatrimoineInputFactory}, appelle {@link PatrimoineProjectionService} /
 * {@link PlacementEvolutionService} puis traduit les types des silos vers les modèles de
 * {@code application-api}. L'écriture passe par {@link PatrimoineCommandService}.
 *
 * <p>DB-041 (prêts, objectifs) reste hors de ce cas d'usage, voir {@link WealthUseCase}.
 */
@Service
public class DefaultWealthUseCase implements WealthUseCase {

    private final PatrimoineProjectionService projectionService;
    private final PlacementEvolutionService evolutionService;
    private final PatrimoineCommandService patrimoineCommandService;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final PatrimoineReader patrimoineReader;
    private final BudgetReader budgetReader;
    private final LoanReader loanReader;
    private final BankReader bankReader;

    public DefaultWealthUseCase(
            PatrimoineProjectionService projectionService,
            PlacementEvolutionService evolutionService,
            PatrimoineCommandService patrimoineCommandService,
            RetirementSettingsReader retirementSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            PatrimoineReader patrimoineReader,
            BudgetReader budgetReader,
            LoanReader loanReader,
            BankReader bankReader) {
        this.projectionService = projectionService;
        this.evolutionService = evolutionService;
        this.patrimoineCommandService = patrimoineCommandService;
        this.retirementSettingsReader = retirementSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.patrimoineReader = patrimoineReader;
        this.budgetReader = budgetReader;
        this.loanReader = loanReader;
        this.bankReader = bankReader;
    }

    private PatrimoineInputFactory.Sources readSources(
            List<com.moe.myfamilybudget.domain.wealth.model.PlacementModel> placements,
            List<com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel> transfers) {
        return new PatrimoineInputFactory.Sources(
                retirementSettingsReader.getRetirementSettings(),
                tresorerieSettingsReader.getTresorerieSettings(),
                economicAssumptionsReader.getEconomicAssumptions().inflationRate(),
                budgetReader.getIncomes(),
                budgetReader.getCharges(),
                placements,
                budgetReader.getOneoffExpenses(),
                transfers);
    }

    @Override
    public WealthResultModel getPatrimoine(boolean useConstantEuros) {
        List<com.moe.myfamilybudget.domain.wealth.model.PlacementModel> placements = patrimoineReader.getPlacements();
        List<com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel> transfers = patrimoineReader.getTransfers();
        com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel projections = projectionService.compute(
                PatrimoineInputFactory.from(readSources(placements, transfers)), useConstantEuros);
        BankImportModel bankImport = bankReader.getBankImport();

        return new WealthResultModel(
                toPlacements(placements),
                toTransfers(transfers),
                toRealEstate(patrimoineReader.getRealEstate()),
                toLoans(loanReader.getLoans()),
                toAssetCategories(patrimoineReader.getAssetCategories()),
                toBankCategories(bankImport),
                toProjections(projections));
    }

    @Override
    public PatrimoineProjectionsModel computePatrimoineProjections(boolean useConstantEuros) {
        List<com.moe.myfamilybudget.domain.wealth.model.PlacementModel> placements = patrimoineReader.getPlacements();
        List<com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel> transfers = patrimoineReader.getTransfers();
        return toProjections(projectionService.compute(
                PatrimoineInputFactory.from(readSources(placements, transfers)), useConstantEuros));
    }

    @Override
    public PlacementEvolutionModel getPlacementEvolution(String placementId, boolean useConstantEuros) {
        List<com.moe.myfamilybudget.domain.wealth.model.PlacementModel> placements = patrimoineReader.getPlacements();
        com.moe.myfamilybudget.domain.wealth.model.PlacementModel placement = placements.stream()
                .filter(p -> Objects.equals(p.id(), placementId))
                .findFirst()
                .orElse(null);
        if (placement == null) {
            return null;
        }
        PlacementEvolution evolution = evolutionService.compute(
                PatrimoineInputFactory.forPlacementEvolution(
                        readSources(placements, patrimoineReader.getTransfers()), placement, LocalDate.now()),
                useConstantEuros);
        return toEvolution(evolution);
    }

    @Override
    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> row) {
        return patrimoineCommandService.savePatrimoineRow(PatrimoineList.fromKey(listKey), row);
    }

    @Override
    public void deletePatrimoineRow(String listKey, String id) {
        patrimoineCommandService.deletePatrimoineRow(PatrimoineList.fromKey(listKey), id);
    }

    @Override
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> entry) {
        return patrimoineCommandService.addPlacementHistoryEntry(placementId, entry);
    }

    @Override
    public void updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> entry) {
        patrimoineCommandService.updatePlacementHistoryEntry(placementId, entryId, entry);
    }

    @Override
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        patrimoineCommandService.deletePlacementHistoryEntry(placementId, entryId);
    }

    private static List<PlacementModel> toPlacements(List<com.moe.myfamilybudget.domain.wealth.model.PlacementModel> placements) {
        if (placements == null) {
            return List.of();
        }
        List<PlacementModel> result = new ArrayList<>(placements.size());
        for (com.moe.myfamilybudget.domain.wealth.model.PlacementModel p : placements) {
            if (p == null) continue;
            result.add(new PlacementModel(
                    p.id(), p.label(), p.category(), p.balance(), p.balanceDate(), p.monthly(),
                    p.monthlyFrom(), p.monthlyUntil(), p.ratePess(), p.rateCorr(), p.rateOpti(),
                    p.excludedFromRetirement(), p.notes(), p.sweepPriority(), p.sweepCap(),
                    p.pauseTriggerBalance(), p.pausePriority(), p.categoryId(), toHistory(p.getEffectiveHistory())));
        }
        return result;
    }

    private static List<PlacementHistoryEntryModel> toHistory(
            List<com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel> history) {
        if (history == null) {
            return List.of();
        }
        List<PlacementHistoryEntryModel> result = new ArrayList<>(history.size());
        for (com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel h : history) {
            result.add(h == null ? null : new PlacementHistoryEntryModel(h.id(), h.date(), h.value(), h.notes()));
        }
        return result;
    }

    private static List<PatrimoineTransferModel> toTransfers(
            List<com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel> transfers) {
        if (transfers == null) {
            return List.of();
        }
        List<PatrimoineTransferModel> result = new ArrayList<>(transfers.size());
        for (com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel t : transfers) {
            result.add(t == null ? null : new PatrimoineTransferModel(t.id(), t.placement(), t.date(), t.amount(), t.notes()));
        }
        return result;
    }

    private static List<com.moe.myfamilybudget.application.usecase.wealth.RealEstateModel> toRealEstate(
            List<com.moe.myfamilybudget.domain.wealth.model.RealEstateModel> realEstate) {
        if (realEstate == null) {
            return List.of();
        }
        List<com.moe.myfamilybudget.application.usecase.wealth.RealEstateModel> result = new ArrayList<>(realEstate.size());
        for (com.moe.myfamilybudget.domain.wealth.model.RealEstateModel r : realEstate) {
            result.add(r == null ? null : new com.moe.myfamilybudget.application.usecase.wealth.RealEstateModel(
                    r.id(), r.label(), r.type(), r.currentValue(), r.valuationYear(), r.annualGrowthRate(), r.notes()));
        }
        return result;
    }

    private static List<LoanModel> toLoans(List<com.moe.myfamilybudget.domain.credit.model.LoanModel> loans) {
        if (loans == null) {
            return List.of();
        }
        List<LoanModel> result = new ArrayList<>(loans.size());
        for (com.moe.myfamilybudget.domain.credit.model.LoanModel l : loans) {
            result.add(l == null ? null : new LoanModel(
                    l.id(), l.label(), l.crd(), l.rate(), l.monthly(), l.insurance(), l.startDate(), l.endDate(),
                    l.initialAmount(), l.totalInstallments(), l.stepDate()));
        }
        return result;
    }

    private static List<AssetCategoryModel> toAssetCategories(
            List<com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel> categories) {
        if (categories == null) {
            return List.of();
        }
        List<AssetCategoryModel> result = new ArrayList<>(categories.size());
        for (com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel c : categories) {
            result.add(c == null ? null : new AssetCategoryModel(c.id(), c.icon(), c.name(), c.bucket(), c.color()));
        }
        return result;
    }

    private static List<BankImportCategoryModel> toBankCategories(BankImportModel bankImport) {
        if (bankImport == null || bankImport.categories() == null) {
            return List.of();
        }
        List<BankImportCategoryModel> result = new ArrayList<>(bankImport.categories().size());
        for (BankImportModel.CategoryModel c : bankImport.categories()) {
            result.add(c == null ? null : new BankImportCategoryModel(c.id(), c.label(), c.kind(), c.compressible()));
        }
        return result;
    }

    private static PatrimoineYearModel toYear(com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel y) {
        return y == null ? null : new PatrimoineYearModel(y.year(), y.pess(), y.corr(), y.opti());
    }

    private static PatrimoinePerPlacementModel toPerPlacement(com.moe.myfamilybudget.domain.wealth.model.PatrimoinePerPlacementModel p) {
        if (p == null) {
            return null;
        }
        List<PatrimoineYearModel> rows = new ArrayList<>();
        if (p.rows() != null) {
            for (com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel row : p.rows()) {
                rows.add(toYear(row));
            }
        }
        return new PatrimoinePerPlacementModel(p.label(), rows);
    }

    private static PatrimoineProjectionsModel toProjections(com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel projections) {
        if (projections == null) {
            return new PatrimoineProjectionsModel(List.of(), List.of());
        }
        List<PatrimoinePerPlacementModel> perPlacement = new ArrayList<>();
        if (projections.perPlacement() != null) {
            for (com.moe.myfamilybudget.domain.wealth.model.PatrimoinePerPlacementModel p : projections.perPlacement()) {
                perPlacement.add(toPerPlacement(p));
            }
        }
        List<PatrimoineYearModel> totals = new ArrayList<>();
        if (projections.totals() != null) {
            for (com.moe.myfamilybudget.domain.wealth.model.PatrimoineYearModel t : projections.totals()) {
                totals.add(toYear(t));
            }
        }
        return new PatrimoineProjectionsModel(perPlacement, totals);
    }

    private static PlacementEvolutionModel toEvolution(PlacementEvolution evolution) {
        if (evolution == null) {
            return null;
        }
        List<PlacementEvolutionModel.Point> points = new ArrayList<>();
        for (PlacementEvolution.Point pt : evolution.points()) {
            points.add(new PlacementEvolutionModel.Point(pt.date(), pt.real(), pt.pess(), pt.corr(), pt.opti()));
        }
        return new PlacementEvolutionModel(evolution.placementId(), evolution.anchorDate(), evolution.today(), points);
    }
}
