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
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@RestController
public class PatrimoineServiceImpl implements PatrimoineApi {

    private final PatrimoineMapper mapper;
    private final PersistenceManager persistenceManager;
    private final PatrimoineProjectionService projectionService;
    private final PlacementEvolutionService evolutionService;
    private final PatrimoineCommandService patrimoineCommandService;

    public PatrimoineServiceImpl(
            PatrimoineMapper mapper,
            PersistenceManager persistenceManager,
            PatrimoineProjectionService projectionService,
            PlacementEvolutionService evolutionService,
            PatrimoineCommandService patrimoineCommandService) {
        this.mapper = mapper;
        this.persistenceManager = persistenceManager;
        this.projectionService = projectionService;
        this.evolutionService = evolutionService;
        this.patrimoineCommandService = patrimoineCommandService;
    }

    @Override
    public ResponseEntity<PatrimoineResponseDto> getPatrimoine(Boolean useConstantEuros) {
        BudgetDataModel data = this.persistenceManager.getBudgetData();
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
        BudgetDataModel data = this.persistenceManager.getBudgetData();
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
