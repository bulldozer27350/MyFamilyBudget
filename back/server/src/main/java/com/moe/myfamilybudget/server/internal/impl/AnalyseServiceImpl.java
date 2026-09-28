package com.moe.myfamilybudget.server.internal.impl;

import com.moe.myfamilybudget.api.controller.AnalyseApi;
import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.server.internal.calculation.AnalyseInput;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.factory.AnalyseInputFactory;
import com.moe.myfamilybudget.server.internal.mapper.AnalyseMapper;
import com.moe.myfamilybudget.server.internal.model.AnalyseCalculator;
import com.moe.myfamilybudget.server.internal.model.AnalyseResultModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI AnalyseApi (Tag: Analyse).
 * Les traitements et calculs sont exécutés exclusivement sur le Modèle Interne du domaine.
 */
@RestController
public class AnalyseServiceImpl implements AnalyseApi {

    private final PersistenceManager persistenceManager;
    private final AnalyseMapper analyseMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final AnalyseInputFactory analyseInputFactory;

    public AnalyseServiceImpl(PersistenceManager persistenceManager, AnalyseMapper analyseMapper,
            ObjectifsSettingsService objectifsSettingsService) {
        this.persistenceManager = persistenceManager;
        this.analyseMapper = analyseMapper;
        this.objectifsSettingsService = objectifsSettingsService;
        this.analyseInputFactory = new AnalyseInputFactory();
    }

    @Override
    public ResponseEntity<AnalyseResponseDto> getAnalyse(Integer monthsBack) {
        BudgetDataModel data = persistenceManager.getBudgetData();
        BankImportModel bankImport = persistenceManager.getBankImport();

        AnalyseInput input = analyseInputFactory.from(data, bankImport, monthsBack);
        AnalyseResultModel resultModel = AnalyseCalculator.computeAnalyse(input);
        AnalyseResponseDto responseDto = analyseMapper.toDto(resultModel, objectifsSettingsService.current(), data);

        return ResponseEntity.ok(responseDto);
    }
}
