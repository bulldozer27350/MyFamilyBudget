package com.moe.myfamilybudget.application.usecase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.command.BankImportCommandService;
import com.moe.myfamilybudget.application.mapper.PointageMapper;
import com.moe.myfamilybudget.application.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.application.model.PointageModel;
import com.moe.myfamilybudget.application.settings.SettingsComposer;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankLigneNotFoundException;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankPointageUseCase;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BankImportCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.calculation.PointageCalculationService;
import com.moe.myfamilybudget.domain.bankpointage.model.AutoMatchResultModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportSummaryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.PendingImportSummaryModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankImportChange;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage Banque et Pointage (R-83, même esprit que {@code DefaultTreasuryUseCase}
 * (R-82), SILO-314).
 *
 * <p>La logique de composition de la réponse (anciennement répartie entre
 * {@code StatementBankImportServiceImpl}, {@code PendingOperationsServiceImpl} et {@code PointageServiceImpl})
 * vit ici, sans {@code ResponseEntity} ni DTO OpenAPI : elle lit ses fragments via les ports des silos
 * ({@link BankReader}, {@link BudgetReader}, {@link PatrimoineReader}, {@link RetirementSettingsReader},
 * {@link TaxSettingsReader}, {@link TresorerieSettingsReader}, {@link SimulationSettingsReader},
 * {@link EconomicAssumptionsReader}), appelle {@link BankImportCalculationService} /
 * {@link PointageCalculationService}, et traduit les types du silo via {@link StatementBankImportMapper} /
 * {@link PointageMapper} (déjà en {@code Map<String, Object>}, contrat REST inchangé). L'écriture passe par
 * {@link BankImportCommandService} ; l'interprétation du {@code listKey} et du {@code field} se fait ici, à la
 * frontière du cas d'usage, comme auparavant à la frontière REST. Les cas « ligne introuvable » et « liste
 * inconnue », auparavant traduits directement en {@code ResponseEntity} dans le contrôleur, sont désormais
 * signalés par {@link BankLigneNotFoundException} et {@link IllegalArgumentException} respectivement : la
 * documentation de {@link BankImportCommandService#modifyBankImport} garantit qu'une exception levée pendant la
 * modification annule la transaction et est propagée telle quelle à l'appelant.
 *
 * <p>Conversion Excel → CSV ({@code convertExcelToCsv}) non reprise ici : utilitaire de format pur qui ne lit ni
 * n'écrit le silo Banque/Pointage (pas de {@link BankReader} ni de {@link BankImportCommandService} impliqués) ;
 * reste porté par {@code StatementBankImportServiceImpl}.
 */
@Service
public class DefaultBankPointageUseCase implements BankPointageUseCase {

    private final BankReader bankReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final BankImportCommandService bankImportCommandService;
    private final BankImportCalculationService bankImportCalculationService;
    private final PointageCalculationService pointageCalculationService;
    private final StatementBankImportMapper bankImportMapper;
    private final PointageMapper pointageMapper;

    public DefaultBankPointageUseCase(
            BankReader bankReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            BankImportCommandService bankImportCommandService,
            BankImportCalculationService bankImportCalculationService,
            PointageCalculationService pointageCalculationService,
            StatementBankImportMapper bankImportMapper,
            PointageMapper pointageMapper) {
        this.bankReader = bankReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.bankImportCommandService = bankImportCommandService;
        this.bankImportCalculationService = bankImportCalculationService;
        this.pointageCalculationService = pointageCalculationService;
        this.bankImportMapper = bankImportMapper;
        this.pointageMapper = pointageMapper;
    }

    // ---------------------------------------------------------------------------
    // Import bancaire
    // ---------------------------------------------------------------------------

    @Override
    public Map<String, Object> getBankImport() {
        return bankImportMapper.toBankImportResponseMap(bankReader.getBankImport());
    }

    @Override
    public void updateBankImportMapping(Map<String, Object> mapping) {
        BankImportModel.BankColumnMappingModel newMapping = bankImportMapper.toColumnMappingModel(mapping);
        // SILO-213 (lot B, etape b2) : lecture, modification et ecriture sous le verrou du silo Banque.
        bankImportCommandService.modifyBankImport(current -> BankImportChange.write(new BankImportModel(
                newMapping,
                current.categories(),
                current.rules(),
                current.transactions(),
                current.pendingOperations(),
                current.matchings()
        ), null));
    }

    @Override
    public void importBankCSV(String csvText) {
        final String csv = csvText != null ? csvText : "";

        // SILO-213 (lot B, etape b2) : lecture, calcul et ecriture sous le verrou du silo Banque.
        bankImportCommandService.modifyBankImport(current -> {
            BankImportModel.BankColumnMappingModel mappingModel = current.columnMapping();

            List<String> colRoles = new ArrayList<>();
            if (mappingModel.dateCol() != null) colRoles.add("date");
            if (mappingModel.labelCol() != null) colRoles.add("label");
            if (mappingModel.amountCol() != null) colRoles.add("amount");

            List<List<String>> rawRows = bankImportCalculationService.parseCSVText(csv, mappingModel.delimiter());
            if (mappingModel.hasHeader() && !rawRows.isEmpty()) {
                rawRows = rawRows.subList(1, rawRows.size());
            }

            BankImportSummaryModel summary = bankImportCalculationService.importTransactions(
                    rawRows, colRoles, mappingModel, current.transactions(), current.rules()
            );

            List<BankImportModel.BankTransactionModel> allTransactions = new ArrayList<>(current.transactions());
            allTransactions.addAll(summary.newTransactions());

            return BankImportChange.write(new BankImportModel(
                    summary.updatedMapping(),
                    current.categories(),
                    current.rules(),
                    allTransactions,
                    current.pendingOperations(),
                    current.matchings()
            ), null);
        });
    }

    @Override
    public Map<String, Object> importBankTransactions(
            List<List<String>> rawRows, List<String> colRoles, Map<String, Object> mapping) {
        // La conversion du mapping demande ne depend pas de l'import courant : elle reste hors verrou.
        BankImportModel.BankColumnMappingModel requestedMapping = mapping != null
                ? bankImportMapper.toColumnMappingModel(mapping)
                : null;

        // SILO-213 (lot B, etape b2) : lecture, calcul et ecriture sous le verrou du silo Banque. Une
        // IllegalArgumentException du calcul (colonnes/lignes incoherentes) annule la transaction et est
        // propagee telle quelle (cf. javadoc de BankImportCommandService#modifyBankImport) ; le contrôleur la
        // traduit en 400.
        BankImportSummaryModel summary = bankImportCommandService.<BankImportSummaryModel>modifyBankImport(current -> {
            BankImportModel.BankColumnMappingModel mappingModel =
                    requestedMapping != null ? requestedMapping : current.columnMapping();

            BankImportSummaryModel result = bankImportCalculationService.importTransactions(
                    rawRows,
                    colRoles,
                    mappingModel,
                    current.transactions(),
                    current.rules()
            );

            List<BankImportModel.BankTransactionModel> allTransactions = new ArrayList<>(
                    current.transactions() != null ? current.transactions() : Collections.emptyList()
            );
            allTransactions.addAll(result.newTransactions());

            return BankImportChange.write(new BankImportModel(
                    result.updatedMapping(),
                    current.categories(),
                    current.rules(),
                    allTransactions,
                    current.pendingOperations(),
                    current.matchings()
            ), result);
        });

        return bankImportMapper.toImportSummaryMap(summary);
    }

    @Override
    public Map<String, Object> forceImportBankTransaction(Map<String, Object> transaction) {
        BankImportModel.BankTransactionModel tx = bankImportMapper.toTransactionModel(transaction);

        // SILO-213 (lot B, etape b2) : lecture, calcul et ecriture sous le verrou du silo Banque.
        BankImportModel.BankTransactionModel finalTx = bankImportCommandService.modifyBankImport(current -> {
            List<BankImportModel.BankImportRuleModel> rules = current.rules() != null ? current.rules() : Collections.emptyList();

            List<BankImportModel.BankTransactionModel> categorized = bankImportCalculationService.applyRulesToTransactions(List.of(tx), rules);
            BankImportModel.BankTransactionModel resolvedTx = (!categorized.isEmpty()) ? categorized.get(0) : tx;

            List<BankImportModel.BankTransactionModel> updatedTxs = new ArrayList<>(
                    current.transactions() != null ? current.transactions() : Collections.emptyList()
            );
            updatedTxs.add(resolvedTx);

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(),
                    current.categories(),
                    current.rules(),
                    updatedTxs,
                    current.pendingOperations(),
                    current.matchings()
            ), resolvedTx);
        });

        return bankImportMapper.toTransactionMap(finalTx);
    }

    @Override
    public void updateBankTransactionSplits(String txId, List<Map<String, Object>> splits) {
        List<BankImportModel.BankTransactionSplitModel> splitModels = bankImportMapper.toSplitModelList(splits);

        // SILO-213 (lot B, etape b2) : lecture, controle et ecriture sous le verrou du silo Banque.
        bankImportCommandService.modifyBankImport(current -> {
            List<BankImportModel.BankTransactionModel> currentTxs = current.transactions();

            int txIndex = -1;
            for (int i = 0; i < currentTxs.size(); i++) {
                if (currentTxs.get(i).id().equals(txId)) {
                    txIndex = i;
                    break;
                }
            }
            if (txIndex == -1) {
                throw new BankLigneNotFoundException("Transaction bancaire introuvable : " + txId);
            }

            BankImportModel.BankTransactionModel existingTx = currentTxs.get(txIndex);
            BankImportModel.BankTransactionModel updatedTx = new BankImportModel.BankTransactionModel(
                    existingTx.id(),
                    existingTx.date(),
                    existingTx.label(),
                    existingTx.type(),
                    existingTx.amount(),
                    existingTx.categoryId(),
                    splitModels
            );

            // IllegalArgumentException propagee telle quelle si la somme des ventilations ne correspond pas
            // au montant de la transaction (cf. javadoc de la classe) ; le contrôleur la traduit en 400.
            updatedTx.validateSplits();

            List<BankImportModel.BankTransactionModel> updatedTxs = new ArrayList<>(currentTxs);
            updatedTxs.set(txIndex, updatedTx);

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(),
                    current.categories(),
                    current.rules(),
                    updatedTxs,
                    current.pendingOperations(),
                    current.matchings()
            ), null);
        });
    }

    @Override
    public Map<String, Object> addBankImportLigne(String listKey, Map<String, Object> row) {
        if ("categories".equals(listKey)) {
            BankImportModel.CategoryModel newCat = bankImportMapper.toCategoryModel(row);
            // SILO-213 (lot B, etape b2) : lecture, modification et ecriture sous le verrou du silo Banque.
            BankImportModel.CategoryModel created = bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.CategoryModel> cats = new ArrayList<>(current.categories());
                cats.add(newCat);
                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), cats, current.rules(),
                        current.transactions(), current.pendingOperations(), current.matchings()),
                        newCat);
            });
            return bankImportMapper.toCategoryMap(created);
        } else if ("rules".equals(listKey)) {
            BankImportModel.BankImportRuleModel newRule = bankImportMapper.toRuleModel(row);
            BankImportModel.BankImportRuleModel created = bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.BankImportRuleModel> rules = new ArrayList<>(current.rules());
                rules.add(newRule);
                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), current.categories(), rules,
                        current.transactions(), current.pendingOperations(), current.matchings()),
                        newRule);
            });
            return bankImportMapper.toRuleMap(created);
        }
        throw new IllegalArgumentException("Liste inconnue pour l'ajout d'une ligne d'import bancaire : " + listKey);
    }

    @Override
    public void updateBankImportLigne(String listKey, String id, String field, Object value) {
        if ("categories".equals(listKey)) {
            // SILO-213 (lot B, etape b2) : lecture, modification et ecriture sous le verrou du silo Banque.
            bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.CategoryModel> cats = current.categories();
                int idx = -1;
                for (int i = 0; i < cats.size(); i++) {
                    if (cats.get(i).id().equals(id)) { idx = i; break; }
                }
                if (idx == -1) {
                    throw new BankLigneNotFoundException("Catégorie introuvable : " + id);
                }

                BankImportModel.CategoryModel existing = cats.get(idx);
                String label = "label".equals(field) ? String.valueOf(value) : existing.label();
                String kind = "kind".equals(field) ? String.valueOf(value) : existing.kind();
                String compressible = "compressible".equals(field) ? String.valueOf(value) : existing.compressible();

                List<BankImportModel.CategoryModel> updated = new ArrayList<>(cats);
                updated.set(idx, new BankImportModel.CategoryModel(existing.id(), label, kind, compressible));
                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), updated, current.rules(),
                        current.transactions(), current.pendingOperations(), current.matchings()),
                        null);
            });
        } else if ("rules".equals(listKey)) {
            bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.BankImportRuleModel> rules = current.rules();
                int idx = -1;
                for (int i = 0; i < rules.size(); i++) {
                    if (rules.get(i).id().equals(id)) { idx = i; break; }
                }
                if (idx == -1) {
                    throw new BankLigneNotFoundException("Règle introuvable : " + id);
                }

                BankImportModel.BankImportRuleModel existing = rules.get(idx);
                String matchText = "matchText".equals(field) ? String.valueOf(value) : existing.matchText();
                String categoryId = "categoryId".equals(field) ? String.valueOf(value) : existing.categoryId();

                List<BankImportModel.BankImportRuleModel> updated = new ArrayList<>(rules);
                updated.set(idx, new BankImportModel.BankImportRuleModel(existing.id(), matchText, categoryId));
                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), current.categories(), updated,
                        current.transactions(), current.pendingOperations(), current.matchings()),
                        null);
            });
        } else {
            throw new IllegalArgumentException("Liste inconnue pour la mise à jour d'une ligne d'import bancaire : " + listKey);
        }
    }

    @Override
    public void removeBankImportLigne(String listKey, String id) {
        if ("categories".equals(listKey)) {
            // SILO-213 (lot B, etape b2) : lecture, modification et ecriture sous le verrou du silo Banque.
            bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.CategoryModel> cats = current.categories().stream()
                        .filter(c -> !c.id().equals(id))
                        .collect(Collectors.toList());
                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), cats, current.rules(),
                        current.transactions(), current.pendingOperations(), current.matchings()),
                        null);
            });
        } else if ("rules".equals(listKey)) {
            bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.BankImportRuleModel> rules = current.rules().stream()
                        .filter(r -> !r.id().equals(id))
                        .collect(Collectors.toList());
                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), current.categories(), rules,
                        current.transactions(), current.pendingOperations(), current.matchings()),
                        null);
            });
        } else {
            throw new IllegalArgumentException("Liste inconnue pour la suppression d'une ligne d'import bancaire : " + listKey);
        }
    }

    @Override
    public void setBankImportTransactionCategory(String txId, String categoryId, String ruleKeyword) {
        // SILO-213 (lot B, etape b2) : lecture, calcul et ecriture sous le verrou du silo Banque.
        bankImportCommandService.modifyBankImport(current -> {
            List<BankImportModel.BankImportRuleModel> currentRules = current.rules() != null ? current.rules() : Collections.emptyList();
            List<BankImportModel.BankImportRuleModel> newRules = new ArrayList<>(currentRules);

            if (ruleKeyword != null && !ruleKeyword.trim().isEmpty()) {
                String key = ruleKeyword.trim().toUpperCase();
                boolean found = false;
                for (int i = 0; i < newRules.size(); i++) {
                    BankImportModel.BankImportRuleModel r = newRules.get(i);
                    if (r.matchText() != null && r.matchText().trim().toUpperCase().equals(key)) {
                        newRules.set(i, new BankImportModel.BankImportRuleModel(r.id(), r.matchText(), categoryId));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    newRules.add(new BankImportModel.BankImportRuleModel(null, ruleKeyword.trim(), categoryId));
                }
            }

            List<BankImportModel.BankTransactionModel> currentTxs = current.transactions() != null ? current.transactions() : Collections.emptyList();
            List<BankImportModel.BankTransactionModel> updatedTxs = new ArrayList<>();
            for (BankImportModel.BankTransactionModel t : currentTxs) {
                if (t.id() != null && t.id().equals(txId)) {
                    updatedTxs.add(new BankImportModel.BankTransactionModel(
                            t.id(), t.date(), t.label(), t.type(), t.amount(), categoryId, t.splits()
                    ));
                } else {
                    updatedTxs.add(t);
                }
            }

            List<BankImportModel.BankTransactionModel> finalTxs = bankImportCalculationService.applyRulesToTransactions(updatedTxs, newRules);

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(),
                    current.categories(),
                    newRules,
                    finalTxs,
                    current.pendingOperations(),
                    current.matchings()
            ), null);
        });
    }

    @Override
    public void recalculateBankImportRules() {
        // SILO-213 (lot B, etape b2) : lecture, calcul et ecriture sous le verrou du silo Banque.
        bankImportCommandService.modifyBankImport(current -> {
            List<BankImportModel.BankTransactionModel> currentTxs = current.transactions() != null ? current.transactions() : Collections.emptyList();
            List<BankImportModel.BankImportRuleModel> currentRules = current.rules() != null ? current.rules() : Collections.emptyList();

            List<BankImportModel.BankTransactionModel> recalculated = bankImportCalculationService.applyRulesToTransactions(currentTxs, currentRules);

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(),
                    current.categories(),
                    currentRules,
                    recalculated,
                    current.pendingOperations(),
                    current.matchings()
            ), null);
        });
    }

    // ---------------------------------------------------------------------------
    // Opérations en attente
    // ---------------------------------------------------------------------------

    @Override
    public Map<String, Object> getPendingOperations() {
        BankImportModel current = bankReader.getBankImport();
        return bankImportMapper.toPendingOperationsResponseMap(
                current,
                budgetReader.getCharges(),
                budgetReader.getIncomes(),
                budgetReader.getOneoffExpenses(),
                SettingsComposer.compose(
                        retirementSettingsReader.getRetirementSettings(), taxSettingsReader.getTaxSettings(),
                        tresorerieSettingsReader.getTresorerieSettings(), simulationSettingsReader.getSimulationSettings(),
                        economicAssumptionsReader.getEconomicAssumptions()));
    }

    @Override
    public Map<String, Object> reconcilePendingOperations() {
        // SILO-213 (lot B, etape b) : lecture, calcul et ecriture sous le verrou du silo Banque.
        AutoMatchResultModel matchResult = bankImportCommandService.modifyBankImport(current -> {
            AutoMatchResultModel result = bankImportCalculationService.autoMatchPendingOperations(
                    current.pendingOperations(), current.transactions()
            );
            if (result.matchCount() <= 0) {
                return BankImportChange.unchanged(result);
            }
            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    result.updatedTransactions(), result.updatedOperations(), current.matchings()
            ), result);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("matchCount", matchResult.matchCount());
        response.put("needsReviewCount", matchResult.needsReviewCount());
        return response;
    }

    @Override
    public void ignorePendingOperation(String operationId) {
        if (operationId == null || operationId.isBlank() || "null".equalsIgnoreCase(operationId)) {
            return;
        }
        bankImportCommandService.modifyBankImport(current -> {
            List<BankImportModel.PendingOperationModel> updatedOps = current.pendingOperations().stream()
                    .map(op -> op.id().equals(operationId)
                            ? new BankImportModel.PendingOperationModel(
                            op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                            op.label(), op.amount(), op.categoryId(), "ignored", op.linkedTxId(),
                            op.clearedDate(), op.notes(), op.splits())
                            : op)
                    .collect(Collectors.toList());

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), updatedOps, current.matchings()
            ), null);
        });
    }

    @Override
    public Map<String, Object> importPendingCB(
            List<List<String>> rawRows, List<String> colRoles, String dateFormat, boolean usePurchaseDate) {
        // SILO-213 (lot B, etape b2) : lecture, calcul et ecriture sous le verrou du silo Banque.
        PendingImportSummaryModel summary = bankImportCommandService.modifyBankImport(current -> {
            PendingImportSummaryModel result = bankImportCalculationService.importPendingCB(
                    rawRows,
                    colRoles,
                    dateFormat,
                    usePurchaseDate,
                    current.pendingOperations(),
                    current.rules()
            );
            if (result.newOperations().isEmpty()) {
                return BankImportChange.unchanged(result);
            }
            List<BankImportModel.PendingOperationModel> allPending = new ArrayList<>(current.pendingOperations());
            allPending.addAll(result.newOperations());
            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), allPending, current.matchings()
            ), result);
        });

        return bankImportMapper.toPendingImportSummaryMap(summary);
    }

    @Override
    public void mergePendingOperation(String manualOpId, Map<String, Object> bankOp) {
        if (manualOpId == null || manualOpId.isBlank()) {
            return;
        }
        BankImportModel.PendingOperationModel bankOpModel = bankImportMapper.toPendingOperationModel(bankOp);

        bankImportCommandService.modifyBankImport(current -> {
            List<BankImportModel.PendingOperationModel> mergedList = bankImportCalculationService.mergePendingOperation(
                    manualOpId,
                    bankOpModel,
                    current.pendingOperations()
            );

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), mergedList, current.matchings()
            ), null);
        });
    }

    @Override
    public void forceImportPendingOperation(Map<String, Object> operation) {
        BankImportModel.PendingOperationModel op = bankImportMapper.toPendingOperationModel(operation);
        if (op == null) {
            return;
        }

        bankImportCommandService.modifyBankImport(current -> {
            BankImportModel.PendingOperationModel resolvedOp = op;
            if ((op.categoryId() == null || op.categoryId().isBlank()) && (op.splits() == null || op.splits().isEmpty())) {
                List<BankImportModel.PendingOperationModel> rulesApplied = bankImportCalculationService.applyRulesToPendingOperations(
                        List.of(op), current.rules()
                );
                if (!rulesApplied.isEmpty()) {
                    resolvedOp = rulesApplied.get(0);
                }
            }
            final BankImportModel.PendingOperationModel opToAdd = resolvedOp;

            List<BankImportModel.PendingOperationModel> updatedList = new ArrayList<>(current.pendingOperations());
            updatedList.removeIf(existing -> existing.id().equals(opToAdd.id()));
            updatedList.add(opToAdd);

            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), updatedList, current.matchings()
            ), null);
        });
    }

    // ---------------------------------------------------------------------------
    // Pointage
    // ---------------------------------------------------------------------------

    @Override
    public Map<String, Object> getPointage() {
        BankImportModel bankImport = bankReader.getBankImport();

        PointageModel internalModel = new PointageModel(
                bankImport.transactions(),
                bankImport.categories(),
                bankImport.matchings(),
                budgetReader.getCharges(),
                budgetReader.getIncomes(),
                patrimoineReader.getPlacements(),
                SettingsComposer.compose(
                        retirementSettingsReader.getRetirementSettings(), taxSettingsReader.getTaxSettings(),
                        tresorerieSettingsReader.getTresorerieSettings(), simulationSettingsReader.getSimulationSettings(),
                        economicAssumptionsReader.getEconomicAssumptions())
        );

        return pointageMapper.toPointageResponseMap(internalModel);
    }

    @Override
    public void savePointageMatching(String monthISO, Object matchingLinksBody) {
        if (monthISO == null || monthISO.isBlank()) {
            throw new IllegalArgumentException("Le paramètre monthISO ne peut être vide.");
        }

        List<BankImportModel.MatchingLinkModel> newLinks = pointageMapper.toMatchingLinks(matchingLinksBody);
        // SILO-213 (lot B, etape b) : lecture, modification et ecriture sous le verrou du silo Banque.
        bankImportCommandService.modifyBankImport(current -> BankImportChange.write(
                pointageCalculationService.updateMatchingForMonth(current, monthISO, newLinks), null));
    }
}
