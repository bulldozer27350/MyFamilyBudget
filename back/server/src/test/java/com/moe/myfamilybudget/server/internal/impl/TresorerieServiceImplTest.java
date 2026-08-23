package com.moe.myfamilybudget.server.internal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.api.model.ChargeDto;
import com.moe.myfamilybudget.api.model.IncomeDto;
import com.moe.myfamilybudget.api.model.OneOffExpenseDto;
import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.api.model.UpdateTresorerieLigneRequestDto;
import com.moe.myfamilybudget.api.model.VariableIncomeDto;
import com.moe.myfamilybudget.api.model.VariableOverrideDto;
import com.moe.myfamilybudget.server.internal.mapper.TresorerieMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.CategoryOptionModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TresorerieResultModel;
import com.moe.myfamilybudget.server.internal.model.TresorerieSuggestionModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.model.VariablePreviewCellModel;
import com.moe.myfamilybudget.server.internal.model.VariablePreviewModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

class TresorerieServiceImplTest {

    private TresorerieServiceImpl service;
    private TresorerieMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new TresorerieMapper();
        persistenceManager = new PersistenceManager();
        persistenceManager.init();
        service = new TresorerieServiceImpl(mapper, persistenceManager);
    }

    // -------------------------------------------------------------------------
    // GET /tresorerie
    // -------------------------------------------------------------------------

    @Test
    void getTresorerie_returnsValidResponse_nominal() {
        ResponseEntity<TresorerieResponseDto> response = service.getTresorerie(false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2049, response.getBody().getRetireYear());
        assertNotNull(response.getBody().getIncomes());
        assertNotNull(response.getBody().getCharges());
        assertNotNull(response.getBody().getCashflow());
        assertNotNull(response.getBody().getCategoryOptions());
        assertFalse(response.getBody().getCategoryOptions().isEmpty());
        assertEquals("", response.getBody().getCategoryOptions().get(0).getValue());
    }

    @Test
    void getTresorerie_returnsValidResponse_constantEuros() {
        ResponseEntity<TresorerieResponseDto> response = service.getTresorerie(true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getCashflow());
        assertFalse(response.getBody().getCashflow().isEmpty());
    }

    // -------------------------------------------------------------------------
    // POST /tresorerie/{listKey} (ADD)
    // -------------------------------------------------------------------------

    @Test
    void addTresorerieLigne_incomes_createsAndAppearsInBudget() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Freelance");
        body.put("monthly", new BigDecimal("2500"));
        body.put("growthRate", new BigDecimal("0.02"));
        body.put("start", "2026-01-01");
        body.put("end", "2035-12-31");

        ResponseEntity<Object> response = service.addTresorerieLigne("incomes", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) response.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);
        assertEquals("Freelance", created.get("label"));

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        IncomeDto income = getResp.getBody().getIncomes().stream()
                .filter(i -> id.equals(i.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(income);
        assertEquals("Freelance", income.getLabel());
        assertEquals(new BigDecimal("2500"), income.getMonthly());
    }

    @Test
    void addTresorerieLigne_charges_createsAndAppearsInBudget() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Abonnement Internet");
        body.put("monthly", new BigDecimal("40"));
        body.put("start", "2026-01-01");

        ResponseEntity<Object> response = service.addTresorerieLigne("charges", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) response.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        ChargeDto charge = getResp.getBody().getCharges().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(charge);
        assertEquals("Abonnement Internet", charge.getLabel());
        assertEquals(new BigDecimal("40"), charge.getMonthly());
    }

    @Test
    void addTresorerieLigne_oneoff_createsAndAppearsInBudget() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Achat Ordinateur");
        body.put("amount", new BigDecimal("1800"));
        body.put("date", "2026-09-15");

        ResponseEntity<Object> response = service.addTresorerieLigne("oneoff", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) response.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        OneOffExpenseDto oneoff = getResp.getBody().getOneoff().stream()
                .filter(o -> id.equals(o.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(oneoff);
        assertEquals("Achat Ordinateur", oneoff.getLabel());
        assertEquals(new BigDecimal("1800"), oneoff.getAmount());
    }

    @Test
    void addTresorerieLigne_variableIncomes_createsAndAppearsInBudget() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Bonus Performance");
        body.put("refIncomeLabel", "Salaire");
        body.put("rate", new BigDecimal("0.15"));
        body.put("startYear", 2026);
        body.put("endYear", 2030);

        ResponseEntity<Object> response = service.addTresorerieLigne("variableIncomes", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) response.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        VariableIncomeDto varInc = getResp.getBody().getVariableIncomes().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(varInc);
        assertEquals("Bonus Performance", varInc.getLabel());
        assertEquals(new BigDecimal("0.15"), varInc.getRate());
    }

    @Test
    void addTresorerieLigne_variableOverrides_createsAndAppearsInBudget() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Bonus Performance");
        body.put("year", 2026);
        body.put("amount", new BigDecimal("4200"));
        body.put("taxable", "Oui");

        ResponseEntity<Object> response = service.addTresorerieLigne("variableOverrides", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) response.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        VariableOverrideDto override = getResp.getBody().getVariableOverrides().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(override);
        assertEquals("Bonus Performance", override.getLabel());
        assertEquals(new BigDecimal("4200"), override.getAmount());
    }

    @Test
    void addTresorerieLigne_placements_createsAndAppearsInBudget() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Compte Titres");
        body.put("balance", new BigDecimal("5000"));
        body.put("monthly", new BigDecimal("300"));

        ResponseEntity<Object> response = service.addTresorerieLigne("placements", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) response.getBody();
        String id = (String) created.get("id");
        assertNotNull(id);

        BudgetDataModel data = persistenceManager.getBudgetData();
        PlacementModel plc = data.getEffectivePlacements().stream()
                .filter(p -> id.equals(p.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(plc);
        assertEquals("Compte Titres", plc.label());
        assertEquals(new BigDecimal("5000"), plc.balance());
    }

    // -------------------------------------------------------------------------
    // PUT /tresorerie/{listKey}/{id} (UPDATE)
    // -------------------------------------------------------------------------

    @Test
    void updateTresorerieLigne_incomes_updatesFieldsSuccessfully() {
        // Ajouter un revenu
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Consulting");
        body.put("monthly", new BigDecimal("2000"));
        ResponseEntity<Object> addResp = service.addTresorerieLigne("incomes", body);
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) addResp.getBody()).get("id");

        // Mise à jour du libellé
        UpdateTresorerieLigneRequestDto dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("label");
        dto.setValue("Consulting Senior");
        service.updateTresorerieLigne("incomes", id, dto);
        // Mise à jour du montant mensuel
        
        dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("monthly");
        dto.setValue(new BigDecimal("3200"));
        service.updateTresorerieLigne("incomes", id, dto);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        IncomeDto updated = getResp.getBody().getIncomes().stream()
                .filter(i -> id.equals(i.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals("Consulting Senior", updated.getLabel());
        assertEquals(new BigDecimal("3200"), updated.getMonthly());
    }

    @Test
    void updateTresorerieLigne_charges_updatesFieldsSuccessfully() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Assurance Auto");
        body.put("monthly", new BigDecimal("80"));
        ResponseEntity<Object> addResp = service.addTresorerieLigne("charges", body);
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) addResp.getBody()).get("id");
        UpdateTresorerieLigneRequestDto dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("monthly");
        dto.setValue(new BigDecimal("95"));

        service.updateTresorerieLigne("charges", id, dto);
        
        dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("notes");
        dto.setValue("Nouvelle formule");
        service.updateTresorerieLigne("charges", id, dto);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        ChargeDto updated = getResp.getBody().getCharges().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("95"), updated.getMonthly());
        assertEquals("Nouvelle formule", updated.getNotes());
    }

    @Test
    void updateTresorerieLigne_oneoff_updatesFieldsSuccessfully() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Travaux");
        body.put("amount", new BigDecimal("5000"));
        body.put("date", "2027-04-01");
        ResponseEntity<Object> addResp = service.addTresorerieLigne("oneoff", body);
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) addResp.getBody()).get("id");
        UpdateTresorerieLigneRequestDto dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("amount");
        dto.setValue(new BigDecimal("6500"));

        service.updateTresorerieLigne("oneoff", id, dto);
        
        dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("date");
        dto.setValue("2027-05-15");
        service.updateTresorerieLigne("oneoff", id, dto);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        OneOffExpenseDto updated = getResp.getBody().getOneoff().stream()
                .filter(o -> id.equals(o.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("6500"), updated.getAmount());
        assertEquals("2027-05-15", updated.getDate());
    }

    @Test
    void updateTresorerieLigne_variableIncomes_updatesFieldsSuccessfully() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Prime Partage");
        body.put("rate", new BigDecimal("0.05"));
        ResponseEntity<Object> addResp = service.addTresorerieLigne("variableIncomes", body);
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) addResp.getBody()).get("id");
        UpdateTresorerieLigneRequestDto dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("rate");
        dto.setValue(new BigDecimal("0.08"));

        service.updateTresorerieLigne("variableIncomes", id, dto);
        
        dto = new UpdateTresorerieLigneRequestDto();
        dto.setField("startYear");
        dto.setValue(2027);
        service.updateTresorerieLigne("variableIncomes", id, dto);

        ResponseEntity<TresorerieResponseDto> getResp = service.getTresorerie(false);
        VariableIncomeDto updated = getResp.getBody().getVariableIncomes().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("0.08"), updated.getRate());
        assertEquals(2027, updated.getStartYear());
    }

    // -------------------------------------------------------------------------
    // DELETE /tresorerie/{listKey}/{id} (REMOVE)
    // -------------------------------------------------------------------------

    @Test
    void removeTresorerieLigne_removesFromDifferentLists() {
        // 1. Incomes
        Map<String, Object> inc = new HashMap<>();
        inc.put("label", "Revenu Test");
        ResponseEntity<Object> addInc = service.addTresorerieLigne("incomes", inc);
        @SuppressWarnings("unchecked")
        String incId = (String) ((Map<String, Object>) addInc.getBody()).get("id");

        ResponseEntity<Void> delIncResp = service.removeTresorerieLigne("incomes", incId);
        assertEquals(HttpStatus.NO_CONTENT, delIncResp.getStatusCode());
        assertFalse(service.getTresorerie(false).getBody().getIncomes().stream().anyMatch(i -> incId.equals(i.getId())));

        // 2. Charges
        Map<String, Object> chg = new HashMap<>();
        chg.put("label", "Charge Test");
        ResponseEntity<Object> addChg = service.addTresorerieLigne("charges", chg);
        @SuppressWarnings("unchecked")
        String chgId = (String) ((Map<String, Object>) addChg.getBody()).get("id");

        ResponseEntity<Void> delChgResp = service.removeTresorerieLigne("charges", chgId);
        assertEquals(HttpStatus.NO_CONTENT, delChgResp.getStatusCode());
        assertFalse(service.getTresorerie(false).getBody().getCharges().stream().anyMatch(c -> chgId.equals(c.getId())));

        // 3. OneOff
        Map<String, Object> one = new HashMap<>();
        one.put("label", "OneOff Test");
        ResponseEntity<Object> addOne = service.addTresorerieLigne("oneoff", one);
        @SuppressWarnings("unchecked")
        String oneId = (String) ((Map<String, Object>) addOne.getBody()).get("id");

        ResponseEntity<Void> delOneResp = service.removeTresorerieLigne("oneoff", oneId);
        assertEquals(HttpStatus.NO_CONTENT, delOneResp.getStatusCode());
        assertFalse(service.getTresorerie(false).getBody().getOneoff().stream().anyMatch(o -> oneId.equals(o.getId())));
    }

    // -------------------------------------------------------------------------
    // POST /tresorerie/adjust (AJUSTEMENT)
    // -------------------------------------------------------------------------

    @Test
    void applyTresorerieAjustement_charges_updatesMonthly() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Facture Électricité");
        body.put("monthly", new BigDecimal("120"));
        ResponseEntity<Object> addResp = service.addTresorerieLigne("charges", body);
        @SuppressWarnings("unchecked")
        String chargeId = (String) ((Map<String, Object>) addResp.getBody()).get("id");

        // Ajuster le montant mensuel à 145€
        TresorerieAjustementRequestDto adjustReq = new TresorerieAjustementRequestDto(chargeId, "charge", BigDecimal.valueOf(145));
        ResponseEntity<Void> resp = service.applyTresorerieAjustement(adjustReq);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        ChargeDto updated = service.getTresorerie(false).getBody().getCharges().stream()
                .filter(c -> chargeId.equals(c.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("145.0"), updated.getMonthly());
    }

    @Test
    void applyTresorerieAjustement_incomes_updatesMonthly() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Salaire Principal");
        body.put("monthly", new BigDecimal("3000"));
        ResponseEntity<Object> addResp = service.addTresorerieLigne("incomes", body);
        @SuppressWarnings("unchecked")
        String incomeId = (String) ((Map<String, Object>) addResp.getBody()).get("id");

        // Ajuster le montant mensuel à 3200€
        TresorerieAjustementRequestDto adjustReq = new TresorerieAjustementRequestDto(incomeId, "revenu", BigDecimal.valueOf(3200));
        ResponseEntity<Void> resp = service.applyTresorerieAjustement(adjustReq);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        IncomeDto updated = service.getTresorerie(false).getBody().getIncomes().stream()
                .filter(i -> incomeId.equals(i.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("3200.0"), updated.getMonthly());
    }

    @Test
    void applyTresorerieAjustement_placements_updatesMonthly() {
        Map<String, Object> body = new HashMap<>();
        body.put("label", "Livret A");
        body.put("monthly", new BigDecimal("200"));
        ResponseEntity<Object> addResp = service.addTresorerieLigne("placements", body);
        @SuppressWarnings("unchecked")
        String plcId = (String) ((Map<String, Object>) addResp.getBody()).get("id");

        TresorerieAjustementRequestDto adjustReq = new TresorerieAjustementRequestDto(plcId, "placement", BigDecimal.valueOf(350));
        ResponseEntity<Void> resp = service.applyTresorerieAjustement(adjustReq);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        PlacementModel updated = persistenceManager.getBudgetData().getEffectivePlacements().stream()
                .filter(p -> plcId.equals(p.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(updated);
        assertEquals(new BigDecimal("350.0"), updated.monthly());
    }

    // -------------------------------------------------------------------------
    // CALCULS & PROJECTIONS TRESORERIE
    // -------------------------------------------------------------------------

    @Test
    void computeTresorerie_withCompleteData_buildsAllFields() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, BigDecimal.valueOf(1000), "2026-01-01", "manual",
                BigDecimal.valueOf(0.02), 21, BigDecimal.valueOf(0.10),
                BigDecimal.valueOf(47100), BigDecimal.valueOf(0.015)
        );

        IncomeModel salary = new IncomeModel(
                "inc_1", "Salaire", BigDecimal.valueOf(3000),
                "2026-01-01", "2049-12-31", BigDecimal.valueOf(0.01), "cat_1", "Notes"
        );

        ChargeModel rent = new ChargeModel(
                "ch_1", "Loyer", BigDecimal.valueOf(1000),
                "2026-01-01", "2049-12-31", BigDecimal.valueOf(0.02), "cat_2", "Loyer principal"
        );

        OneOffExpenseModel travel = new OneOffExpenseModel(
                "one_1", "Voyage", "2026-07-15", BigDecimal.valueOf(1500), "Vacances été"
        );

        VariableIncomeModel bonus = new VariableIncomeModel(
                "var_1", "Prime annuelle", "Salaire", BigDecimal.valueOf(0.10),
                2026, 2049, "Oui"
        );

        VariableOverrideModel bonusReal = new VariableOverrideModel(
                "ov_1", "Prime annuelle", 2026, BigDecimal.valueOf(3500), "Oui"
        );

        BankImportModel.CategoryModel cat1 = new BankImportModel.CategoryModel("cat_1", "Revenus");
        BankImportModel.CategoryModel cat2 = new BankImportModel.CategoryModel("cat_2", "Logement");
        BankImportModel bankImport = new BankImportModel(
                List.of(
                        new BankImportModel.BankTransactionModel("tx_1", "2026-01-10", "Loyer Janvier", BigDecimal.valueOf(-1050)),
                        new BankImportModel.BankTransactionModel("tx_2", "2026-02-10", "Loyer Février", BigDecimal.valueOf(-1050)),
                        new BankImportModel.BankTransactionModel("tx_3", "2026-03-10", "Loyer Mars", BigDecimal.valueOf(-1050))
                ),
                List.of(cat1, cat2),
                List.of(
                        new BankImportModel.MatchingModel("2026-01", List.of(new BankImportModel.MatchingLinkModel("ch_1", List.of("tx_1")))),
                        new BankImportModel.MatchingModel("2026-02", List.of(new BankImportModel.MatchingLinkModel("ch_1", List.of("tx_2")))),
                        new BankImportModel.MatchingModel("2026-03", List.of(new BankImportModel.MatchingLinkModel("ch_1", List.of("tx_3"))))
                )
        );

        BudgetDataModel data = new BudgetDataModel(
                settings,
                List.of(salary),
                List.of(rent),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(travel),
                List.of(),
                List.of(bonus),
                List.of(bonusReal),
                bankImport
        );

        TresorerieResultModel result = service.computeTresorerie(data, false);

        assertNotNull(result);
        assertEquals(2049, result.retireYear());
        assertEquals(1, result.incomes().size());
        assertEquals(1, result.charges().size());
        assertEquals(1, result.oneoff().size());
        assertEquals(1, result.variableIncomes().size());
        assertEquals(1, result.variableOverrides().size());

        // Labels
        assertEquals(List.of("Salaire"), result.incomeLabels());
        assertEquals(List.of("Prime annuelle"), result.variableIncomeLabels());

        // Category options
        assertEquals(3, result.categoryOptions().size());
        assertEquals("", result.categoryOptions().get(0).value());
        assertEquals("— Non liée —", result.categoryOptions().get(0).label());
        assertEquals("Logement", result.categoryOptions().get(1).label());
        assertEquals("Revenus", result.categoryOptions().get(2).label());

        // Suggestions: Loyer budgeté 1000€, réel constaté 1050€ => écart 50€ (>= 10€)
        assertFalse(result.suggestions().isEmpty());
        TresorerieSuggestionModel suggestion = result.suggestions().get(0);
        assertEquals("ch_1", suggestion.id());
        assertEquals("Loyer", suggestion.label());
        assertEquals("charge", suggestion.kind());
        assertEquals(new BigDecimal("1000.00"), suggestion.budgeted());
        assertEquals(new BigDecimal("1050.00"), suggestion.avg3m());
        assertEquals(new BigDecimal("50.00"), suggestion.ecart());
        assertEquals(new BigDecimal("5.00"), suggestion.ecartPct());

        // Variable preview
        assertFalse(result.variablePreview().isEmpty());
        VariablePreviewModel preview = result.variablePreview().get(0);
        assertEquals("Prime annuelle", preview.label());
        assertEquals(4, preview.cells().size());
        // Cell 2026 is overridden (real) with 3500
        VariablePreviewCellModel cell2026 = preview.cells().get(0);
        assertEquals(2026, cell2026.year());
        assertEquals(new BigDecimal("3500"), cell2026.amount());
        assertTrue(cell2026.isReal());
    }

    @Test
    void buildCategoryOptions_sortsFrenchCollatorAndHandlesEmptyCategories() {
        BankImportModel.CategoryModel catA = new BankImportModel.CategoryModel("cat_a", "Épargne");
        BankImportModel.CategoryModel catB = new BankImportModel.CategoryModel("cat_b", "Alimentation");
        BankImportModel.CategoryModel catC = new BankImportModel.CategoryModel("cat_c", "Énergie");

        BudgetDataModel data = new BudgetDataModel(
                null, List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new BankImportModel(List.of(), List.of(catA, catB, catC), List.of())
        );

        List<CategoryOptionModel> options = service.buildCategoryOptions(data);
        assertEquals(4, options.size()); // 1 default + 3 sorted
        assertEquals("", options.get(0).value());
        assertEquals("Alimentation", options.get(1).label());
        assertEquals("Énergie", options.get(2).label());
        assertEquals("Épargne", options.get(3).label());
    }
}
