package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PatrimoineApi;
import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.UpdatePlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionDto;
import com.moe.myfamilybudget.api.model.PlacementHistoryEntryDto;
import com.moe.myfamilybudget.server.internal.calculation.PatrimoineProjectionService;
import com.moe.myfamilybudget.server.internal.calculation.PlacementEvolution;
import com.moe.myfamilybudget.server.internal.calculation.PlacementEvolutionService;
import com.moe.myfamilybudget.server.internal.factory.PatrimoineInputFactory;
import com.moe.myfamilybudget.server.internal.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.command.PatrimoineCommandService;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.BudgetReader;
import com.moe.myfamilybudget.server.internal.port.LoanReader;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;

/**
 * RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Les lectures passent par les ports de domaine ({@link
 * SettingsReader}, {@link PatrimoineReader}, {@link BudgetReader}, {@link LoanReader}, {@link
 * BankReader}) ; le {@link BudgetDataModel} attendu par {@link PatrimoineInputFactory} et par
 * {@link PatrimoineMapper#toPatrimoineResponseDto} est recomposé localement à partir de ces ports,
 * avec les domaines non lus laissés à {@code null}.
 */
@RestController
public class PatrimoineServiceImpl implements PatrimoineApi {

    private final PatrimoineMapper mapper;
    private final PatrimoineProjectionService projectionService;
    private final PlacementEvolutionService evolutionService;
    private final PatrimoineCommandService patrimoineCommandService;
    private final SettingsReader settingsReader;
    private final PatrimoineReader patrimoineReader;
    private final BudgetReader budgetReader;
    private final LoanReader loanReader;
    private final BankReader bankReader;

    public PatrimoineServiceImpl(
            PatrimoineMapper mapper,
            PatrimoineProjectionService projectionService,
            PlacementEvolutionService evolutionService,
            PatrimoineCommandService patrimoineCommandService,
            SettingsReader settingsReader,
            PatrimoineReader patrimoineReader,
            BudgetReader budgetReader,
            LoanReader loanReader,
            BankReader bankReader) {
        this.mapper = mapper;
        this.projectionService = projectionService;
        this.evolutionService = evolutionService;
        this.patrimoineCommandService = patrimoineCommandService;
        this.settingsReader = settingsReader;
        this.patrimoineReader = patrimoineReader;
        this.budgetReader = budgetReader;
        this.loanReader = loanReader;
        this.bankReader = bankReader;
    }

    private BudgetDataModel composeBudgetData() {
        return new BudgetDataModel(
                settingsReader.getSettings(), budgetReader.getIncomes(), budgetReader.getCharges(),
                patrimoineReader.getPlacements(), patrimoineReader.getRealEstate(), null, null, null, null, null,
                budgetReader.getOneoffExpenses(), patrimoineReader.getTransfers(), null, null,
                bankReader.getBankImport(), patrimoineReader.getAssetCategories(), loanReader.getLoans(), null);
    }

    @Override
    public ResponseEntity<PatrimoineResponseDto> getPatrimoine(Boolean useConstantEuros) {
        BudgetDataModel data = composeBudgetData();
        PatrimoineProjectionsModel projections = computePatrimoineProjections(data, Boolean.TRUE.equals(useConstantEuros));
        PatrimoineResponseDto response = this.mapper.toPatrimoineResponseDto(data, projections);
        return ResponseEntity.ok(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> savePatrimoineLigne(String listKey, Object body) {
        Map<String, Object> map = (body instanceof Map) ? (Map<String, Object>) body : null;
        this.patrimoineCommandService.savePatrimoineRow(listKey, map);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePatrimoineLigne(String listKey, String id) {
        this.patrimoineCommandService.deletePatrimoineRow(listKey, id);
        return ResponseEntity.noContent().build();
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
        BudgetDataModel data = composeBudgetData();
        PlacementModel placement = data.getEffectivePlacements().stream()
                .filter(p -> java.util.Objects.equals(p.id(), placementId))
                .findFirst()
                .orElse(null);
        if (placement == null) {
            return ResponseEntity.notFound().build();
        }
        PlacementEvolution evolution = this.evolutionService.compute(
                PatrimoineInputFactory.forPlacementEvolution(data, placement, LocalDate.now()),
                Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toPlacementEvolutionDto(evolution));
    }

    /**
     * Façade mince (RF-301) : projection annuelle déléguée à {@link PatrimoineProjectionService}.
     * Conservée publique pour les appelants existants.
     */
    public PatrimoineProjectionsModel computePatrimoineProjections(BudgetDataModel data, boolean useConstantEuros) {
        return this.projectionService.compute(PatrimoineInputFactory.from(data), useConstantEuros);
    }
}
