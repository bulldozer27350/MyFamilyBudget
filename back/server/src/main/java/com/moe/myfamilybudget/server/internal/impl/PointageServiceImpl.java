package com.moe.myfamilybudget.server.internal.impl;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PointageApi;
import com.moe.myfamilybudget.server.internal.mapper.PointageMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.PointageCalculator;
import com.moe.myfamilybudget.server.internal.model.PointageModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI PointageApi (Tag: Pointage).
 * Les traitements et calculs sont exécutés exclusivement sur le Modèle Interne du domaine.
 */
@Service
@RestController
public class PointageServiceImpl implements PointageApi {

    private final PersistenceManager persistenceManager;
    private final PointageMapper mapper;

    public PointageServiceImpl(PersistenceManager persistenceManager, PointageMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Object> getPointage() {
        BudgetDataModel budgetData = persistenceManager.getBudgetData();
        BankImportModel bankImport = persistenceManager.getBankImport();

        PointageModel internalModel = new PointageModel(
                bankImport.transactions(),
                bankImport.categories(),
                bankImport.matchings(),
                budgetData.charges(),
                budgetData.incomes(),
                budgetData.placements(),
                budgetData.settings()
        );

        Map<String, Object> responseMap = mapper.toPointageResponseMap(internalModel);
        return ResponseEntity.ok(responseMap);
    }

    @Override
    public ResponseEntity<Void> savePointageMatching(String monthISO, Object body) {
        if (monthISO == null || monthISO.isBlank()) {
            throw new IllegalArgumentException("Le paramètre monthISO ne peut être vide.");
        }

        List<BankImportModel.MatchingLinkModel> newLinks = mapper.toMatchingLinks(body);
        BankImportModel currentImport = persistenceManager.getBankImport();

        BankImportModel updatedImport = PointageCalculator.updateMatchingForMonth(currentImport, monthISO, newLinks);
        persistenceManager.updateBankImport(updatedImport);

        return ResponseEntity.ok().build();
    }
}
