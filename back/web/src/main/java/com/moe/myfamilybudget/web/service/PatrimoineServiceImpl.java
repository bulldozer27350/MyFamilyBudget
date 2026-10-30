package com.moe.myfamilybudget.web.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PatrimoineApi;
import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.UpdatePlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionDto;
import com.moe.myfamilybudget.api.model.PlacementHistoryEntryDto;
import com.moe.myfamilybudget.application.command.GoalCommandService;
import com.moe.myfamilybudget.application.command.LoanCommandService;
import com.moe.myfamilybudget.web.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementEvolutionModel;
import com.moe.myfamilybudget.application.usecase.wealth.WealthResultModel;
import com.moe.myfamilybudget.application.usecase.wealth.WealthUseCase;

/**
 * R-81 (même gabarit que {@code ImpotsServiceImpl}, SILO-312) : ce service ne garde que la conversion
 * vers le monde HTTP ({@link ResponseEntity}, DTO). La composition de la réponse (lectures des
 * fragments, construction de l'entrée du moteur, appel du moteur, traduction des types des silos) est
 * portée par le cas d'usage {@link WealthUseCase} ({@code application-api}), implémenté par
 * {@code DefaultWealthUseCase}.
 *
 * <p>DB-041 : les prêts ("loans", alias "credits") et les objectifs ("objectifs") n'appartiennent pas
 * au cas d'usage Patrimoine : ce routage générique par {@code listKey} reste ici, comme avant R-81
 * (même mécanisme que pour {@code settingsCommandRouter} dans {@code ImpotsServiceImpl}, R-80).
 *
 * <p>RF-B01 (doc/architecture/13-persistance.md) : plus d'appel direct à {@code PersistenceManager}.
 */
@RestController
public class PatrimoineServiceImpl implements PatrimoineApi {

    private final PatrimoineMapper mapper;
    private final WealthUseCase wealthUseCase;
    private final LoanCommandService loanCommandService;
    private final GoalCommandService goalCommandService;

    public PatrimoineServiceImpl(
            PatrimoineMapper mapper,
            WealthUseCase wealthUseCase,
            LoanCommandService loanCommandService,
            GoalCommandService goalCommandService) {
        this.mapper = mapper;
        this.wealthUseCase = wealthUseCase;
        this.loanCommandService = loanCommandService;
        this.goalCommandService = goalCommandService;
    }

    @Override
    public ResponseEntity<PatrimoineResponseDto> getPatrimoine(Boolean useConstantEuros) {
        WealthResultModel resultModel = wealthUseCase.getPatrimoine(Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(mapper.toPatrimoineResponseDto(resultModel));
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
            wealthUseCase.savePatrimoineRow(listKey, map);
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
            wealthUseCase.deletePatrimoineRow(listKey, id);
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
        Map<String, Object> map = new HashMap<>();
        map.put("date", body.getDate());
        map.put("value", body.getValue());
        map.put("notes", body.getNotes());
        Map<String, Object> saved = wealthUseCase.addPlacementHistoryEntry(placementId, map);
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
            Map<String, Object> map = new HashMap<>();
            if (body.getDate() != null) map.put("date", body.getDate());
            if (body.getValue() != null) map.put("value", body.getValue());
            if (body.getNotes() != null) map.put("notes", body.getNotes());
            wealthUseCase.updatePlacementHistoryEntry(placementId, entryId, map);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePlacementHistoriquePoint(String placementId, String entryId) {
        wealthUseCase.deletePlacementHistoryEntry(placementId, entryId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PlacementEvolutionDto> getPlacementEvolution(String placementId, Boolean useConstantEuros) {
        PlacementEvolutionModel evolution = wealthUseCase.getPlacementEvolution(placementId, Boolean.TRUE.equals(useConstantEuros));
        if (evolution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(this.mapper.toPlacementEvolutionDto(evolution));
    }

    /**
     * Façade mince (RF-301) : projection annuelle déléguée à {@link WealthUseCase}. Visibilité réduite
     * au package ({@code PatrimoineServiceImplTest} est dans le même package).
     */
    PatrimoineProjectionsModel computePatrimoineProjections(boolean useConstantEuros) {
        return wealthUseCase.computePatrimoineProjections(useConstantEuros);
    }
}
