package com.moe.myfamilybudget.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.SettingsDto;
import com.moe.myfamilybudget.application.snapshot.GlobalBudgetSnapshotService;
import com.moe.myfamilybudget.application.usecase.system.SystemUseCase;

/**
 * Tests unitaires de {@link DefaultSystemUseCase} (R-88, SILO-319).
 */
class DefaultSystemUseCaseTest {

    private GlobalBudgetSnapshotService snapshotService;
    private SystemUseCase useCase;

    @BeforeEach
    void setUp() {
        snapshotService = mock(GlobalBudgetSnapshotService.class);
        useCase = new DefaultSystemUseCase(snapshotService, new ObjectMapper());
    }

    @Test
    @DisplayName("exportBudget délègue à GlobalBudgetSnapshotService et convertit en Map")
    void exportBudget() {
        BudgetDataDto dto = new BudgetDataDto();
        SettingsDto settings = new SettingsDto();
        settings.setBirthYear(1990);
        dto.setSettings(settings);
        when(snapshotService.export()).thenReturn(dto);

        Map<String, Object> result = useCase.exportBudget();

        assertNotNull(result);
        verify(snapshotService).export();
    }

    @Test
    @DisplayName("importBudget convertit la Map en DTO et délègue à GlobalBudgetSnapshotService")
    void importBudget() {
        BudgetDataDto dto = new BudgetDataDto();
        when(snapshotService.importSnapshot(any())).thenReturn(dto);

        Map<String, Object> input = Map.of("settings", Map.of("birthYear", 1990));
        Map<String, Object> result = useCase.importBudget(input);

        assertNotNull(result);
        verify(snapshotService).importSnapshot(any(BudgetDataDto.class));
    }

    @Test
    @DisplayName("resetBudget délègue à GlobalBudgetSnapshotService")
    void resetBudget() {
        BudgetDataDto dto = new BudgetDataDto();
        when(snapshotService.reset()).thenReturn(dto);

        Map<String, Object> result = useCase.resetBudget();

        assertNotNull(result);
        verify(snapshotService).reset();
    }
}
