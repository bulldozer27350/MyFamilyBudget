package com.moe.myfamilybudget.server.internal.impl;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PointageApi;
import com.moe.myfamilybudget.server.internal.command.BankImportCommandService;
import com.moe.myfamilybudget.server.internal.mapper.PointageMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.PointageCalculator;
import com.moe.myfamilybudget.server.internal.model.PointageModel;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.BudgetReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI PointageApi (Tag: Pointage).
 * Les traitements et calculs sont exécutés exclusivement sur le Modèle Interne du domaine.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Lecture via {@link BankReader}, {@link BudgetReader},
 * {@link PatrimoineReader} et {@link SettingsReader} ; écriture via
 * {@link BankImportCommandService} (déjà en place depuis RF-A00).
 */
@Service
@RestController
public class PointageServiceImpl implements PointageApi {

    private final BankReader bankReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final SettingsReader settingsReader;
    private final BankImportCommandService bankImportCommandService;
    private final PointageMapper mapper;

    public PointageServiceImpl(
            BankReader bankReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            SettingsReader settingsReader,
            BankImportCommandService bankImportCommandService,
            PointageMapper mapper) {
        this.bankReader = bankReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.settingsReader = settingsReader;
        this.bankImportCommandService = bankImportCommandService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Object> getPointage() {
        BankImportModel bankImport = bankReader.getBankImport();

        PointageModel internalModel = new PointageModel(
                bankImport.transactions(),
                bankImport.categories(),
                bankImport.matchings(),
                budgetReader.getCharges(),
                budgetReader.getIncomes(),
                patrimoineReader.getPlacements(),
                settingsReader.getSettings()
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
        BankImportModel currentImport = bankReader.getBankImport();

        BankImportModel updatedImport = PointageCalculator.updateMatchingForMonth(currentImport, monthISO, newLinks);
        bankImportCommandService.updateBankImport(updatedImport);

        return ResponseEntity.ok().build();
    }
}
