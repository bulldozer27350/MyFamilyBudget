package com.moe.myfamilybudget.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PatrimoineApi;
import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.UpdatePlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionDto;
import com.moe.myfamilybudget.api.model.PlacementHistoryEntryDto;
import com.moe.myfamilybudget.domain.wealth.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolution;
import com.moe.myfamilybudget.domain.wealth.calculation.PlacementEvolutionService;
import com.moe.myfamilybudget.application.factory.PatrimoineInputFactory;
import com.moe.myfamilybudget.application.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;
import com.moe.myfamilybudget.application.command.GoalCommandService;
import com.moe.myfamilybudget.application.command.LoanCommandService;
import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineList;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;

/**
 * RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. SILO-112 : plus de {@code BudgetDataModel} ; chaque fragment est lu chez son
 * propriétaire ({@link RetirementSettingsReader}, {@link TresorerieSettingsReader},
 * {@link EconomicAssumptionsReader}, {@link PatrimoineReader}, {@link BudgetReader}, {@link LoanReader},
 * {@link BankReader}) et transmis à {@link PatrimoineInputFactory} et à
 * {@link PatrimoineMapper#toPatrimoineResponseDto}.
 */
@RestController
public class PatrimoineServiceImpl implements PatrimoineApi {

    private final PatrimoineMapper mapper;
    private final PatrimoineProjectionService projectionService;
    private final PlacementEvolutionService evolutionService;
    private final PatrimoineCommandService patrimoineCommandService;
    private final LoanCommandService loanCommandService;
    private final GoalCommandService goalCommandService;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final PatrimoineReader patrimoineReader;
    private final BudgetReader budgetReader;
    private final LoanReader loanReader;
    private final BankReader bankReader;

    public PatrimoineServiceImpl(
            PatrimoineMapper mapper,
            PatrimoineProjectionService projectionService,
            PlacementEvolutionService evolutionService,
            PatrimoineCommandService patrimoineCommandService,
            LoanCommandService loanCommandService,
            GoalCommandService goalCommandService,
            RetirementSettingsReader retirementSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            PatrimoineReader patrimoineReader,
            BudgetReader budgetReader,
            LoanReader loanReader,
            BankReader bankReader) {
        this.mapper = mapper;
        this.projectionService = projectionService;
        this.evolutionService = evolutionService;
        this.patrimoineCommandService = patrimoineCommandService;
        this.loanCommandService = loanCommandService;
        this.goalCommandService = goalCommandService;
        this.retirementSettingsReader = retirementSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.patrimoineReader = patrimoineReader;
        this.budgetReader = budgetReader;
        this.loanReader = loanReader;
        this.bankReader = bankReader;
    }

    private PatrimoineInputFactory.Sources readSources(List<PlacementModel> placements, List<PatrimoineTransferModel> transfers) {
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
    public ResponseEntity<PatrimoineResponseDto> getPatrimoine(Boolean useConstantEuros) {
        List<PlacementModel> placements = patrimoineReader.getPlacements();
        List<PatrimoineTransferModel> transfers = patrimoineReader.getTransfers();
        PatrimoineProjectionsModel projections = this.projectionService.compute(
                PatrimoineInputFactory.from(readSources(placements, transfers)),
                Boolean.TRUE.equals(useConstantEuros));
        PatrimoineResponseDto response = this.mapper.toPatrimoineResponseDto(
                placements,
                transfers,
                patrimoineReader.getRealEstate(),
                loanReader.getLoans(),
                patrimoineReader.getAssetCategories(),
                bankReader.getBankImport(),
                projections);
        return ResponseEntity.ok(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> savePatrimoineLigne(String listKey, Object body) {
        Map<String, Object> map = (body instanceof Map) ? (Map<String, Object>) body : null;
        if (isLoanList(listKey)) {
            this.loanCommandService.saveLoanRow(map);
        } else if (isGoalList(listKey)) {
            this.goalCommandService.saveGoalRow(map);
        } else {
            this.patrimoineCommandService.savePatrimoineRow(PatrimoineList.fromKey(listKey), map);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePatrimoineLigne(String listKey, String id) {
        if (isLoanList(listKey)) {
            this.loanCommandService.deleteLoanRow(id);
        } else if (isGoalList(listKey)) {
            this.goalCommandService.deleteGoalRow(id);
        } else {
            this.patrimoineCommandService.deletePatrimoineRow(PatrimoineList.fromKey(listKey), id);
        }
        return ResponseEntity.noContent().build();
    }

    /** DB-041 : les prets ("loans", alias "credits") sont ecrits par le command service Credit. */
    private static boolean isLoanList(String listKey) {
        return "loans".equalsIgnoreCase(listKey) || "credits".equalsIgnoreCase(listKey);
    }

    /** DB-041 : les objectifs sont ecrits par le command service Objectifs. */
    private static boolean isGoalList(String listKey) {
        return "objectifs".equalsIgnoreCase(listKey);
    }

    @Override
    public ResponseEntity<PlacementHistoryEntryDto> addPlacementHistoriquePoint(String placementId, AddPlacementHistoriquePointRequest body) {
        if (body == null) return ResponseEntity.ok().build();
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("date", body.getDate());
        map.put("value", body.getValue());
        map.put("notes", body.getNotes());
        Map<String, Object> saved = this.patrimoineCommandService.addPlacementHistoryEntry(placementId, map);
        PlacementHistoryEntryDto dto = new PlacementHistoryEntryDto();
        dto.setId((String) saved.get("id"));
        dto.setDate((String) saved.get("date"));
        Object value = saved.get("value");
        if (value instanceof BigDecimal bd) dto.setValue(bd);
        dto.setNotes((String) saved.get("notes"));
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Void> updatePlacementHistoriquePoint(String placementId, String entryId, UpdatePlacementHistoriquePointRequest body) {
        if (body != null) {
            Map<String, Object> map = new java.util.HashMap<>();
            if (body.getDate() != null) map.put("date", body.getDate());
            if (body.getValue() != null) map.put("value", body.getValue());
            if (body.getNotes() != null) map.put("notes", body.getNotes());
            this.patrimoineCommandService.updatePlacementHistoryEntry(placementId, entryId, map);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePlacementHistoriquePoint(String placementId, String entryId) {
        this.patrimoineCommandService.deletePlacementHistoryEntry(placementId, entryId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PlacementEvolutionDto> getPlacementEvolution(String placementId, Boolean useConstantEuros) {
        List<PlacementModel> placements = patrimoineReader.getPlacements();
        PlacementModel placement = placements.stream()
                .filter(p -> java.util.Objects.equals(p.id(), placementId))
                .findFirst()
                .orElse(null);
        if (placement == null) {
            return ResponseEntity.notFound().build();
        }
        PlacementEvolution evolution = this.evolutionService.compute(
                PatrimoineInputFactory.forPlacementEvolution(
                        readSources(placements, patrimoineReader.getTransfers()), placement, LocalDate.now()),
                Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toPlacementEvolutionDto(evolution));
    }

    /**
     * Façade mince (RF-301) : projection annuelle déléguée à {@link PatrimoineProjectionService}.
     * Visibilité réduite au package ({@code PatrimoineServiceImplTest} est dans le même package).
     */
    PatrimoineProjectionsModel computePatrimoineProjections(boolean useConstantEuros) {
        return this.projectionService.compute(
                PatrimoineInputFactory.from(readSources(patrimoineReader.getPlacements(), patrimoineReader.getTransfers())),
                useConstantEuros);
    }
}
