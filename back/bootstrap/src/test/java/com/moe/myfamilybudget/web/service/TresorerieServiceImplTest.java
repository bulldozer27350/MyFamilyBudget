package com.moe.myfamilybudget.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moe.myfamilybudget.domain.bankpointage.core.DefaultPointageCalculationService;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.core.DefaultTaxCalculationService;
import com.moe.myfamilybudget.domain.treasury.core.DefaultTresorerieCalculationService;
import com.moe.myfamilybudget.api.model.ChargeDto;
import com.moe.myfamilybudget.api.model.IncomeDto;
import com.moe.myfamilybudget.api.model.OneOffExpenseDto;
import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.api.model.UpdateTresorerieLigneRequestDto;
import com.moe.myfamilybudget.api.model.VariableIncomeDto;
import com.moe.myfamilybudget.api.model.VariableOverrideDto;
import com.moe.myfamilybudget.application.command.TresorerieCommandService;
import com.moe.myfamilybudget.web.mapper.TresorerieMapper;
import com.moe.myfamilybudget.application.usecase.DefaultTreasuryUseCase;
import com.moe.myfamilybudget.application.usecase.treasury.TreasuryUseCase;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * R-82 (meme gabarit que {@code ImpotsServiceImplTest}) : {@code TresorerieServiceImpl} ne garde plus que
 * la conversion HTTP ; la composition de la reponse et l'ecriture passent par {@link DefaultTreasuryUseCase}
 * (voir {@code DefaultTreasuryUseCaseTest} pour la couverture detaillee du cas d'usage, y compris
 * {@code computeTresorerie}/{@code buildCategoryOptions}, desormais prives). Ce test verifie uniquement la
 * traduction HTTP (ajout/mise a jour/suppression de lignes, ajustement, lecture).
 */
class TresorerieServiceImplTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();

    private TresorerieServiceImpl service;
    private TresorerieMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TresorerieMapper();
        InMemoryTreasuryStore treasuryStore = new InMemoryTreasuryStore();
        InMemoryRetirementStore retirementStore = new InMemoryRetirementStore();
        InMemoryTaxStore taxStore = new InMemoryTaxStore();
        CacheBackedAppSettings appSettings = new CacheBackedAppSettings();
        TreasuryUseCase treasuryUseCase = new DefaultTreasuryUseCase(
                new TresorerieCommandService(treasuryStore, silos -> { }, RecordingTransactionRunner.direct()),
                new DefaultRetirementCalculationService(),
                new DefaultTaxCalculationService(),
                new DefaultTresorerieCalculationService(),
                new DefaultPointageCalculationService(),
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                taxStore,
                treasuryStore,
                new PatrimoinePersistenceAdapter(new InMemoryWealthStore().getDelegate(), treasuryStore.getDelegate()),
                bankStore);
        service = new TresorerieServiceImpl(mapper, treasuryUseCase);
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

    @Test
    void updateTresorerieLigne_unknownFieldOrList_isRejected() {
        ResponseEntity<Object> addResp = service.addTresorerieLigne("charges", new HashMap<>());
        @SuppressWarnings("unchecked")
        String id = (String) ((Map<String, Object>) addResp.getBody()).get("id");

        UpdateTresorerieLigneRequestDto unknownField = new UpdateTresorerieLigneRequestDto();
        unknownField.setField("inconnu");
        unknownField.setValue("x");
        assertThrows(com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException.class,
                () -> service.updateTresorerieLigne("charges", id, unknownField));

        // champ connu mais sans objet pour la liste visee : toujours refuse par les updaters
        UpdateTresorerieLigneRequestDto wrongList = new UpdateTresorerieLigneRequestDto();
        wrongList.setField("ratePess");
        wrongList.setValue(new BigDecimal("0.03"));
        assertThrows(com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException.class,
                () -> service.updateTresorerieLigne("charges", id, wrongList));

        UpdateTresorerieLigneRequestDto ok = new UpdateTresorerieLigneRequestDto();
        ok.setField("label");
        ok.setValue("x");
        assertThrows(IllegalArgumentException.class, () -> service.updateTresorerieLigne("inconnue", id, ok));
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

}
