package com.moe.myfamilybudget.web.service;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.ImpotsApi;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.web.mapper.TaxMapper;
import com.moe.myfamilybudget.application.usecase.tax.TaxResultModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxUseCase;

/**
 * Point d'entrée REST du domaine Fiscalité.
 *
 * <p>R-80 (même gabarit que {@code RetraiteServiceImpl}, SILO-311) : ce service ne garde que la
 * conversion vers le monde HTTP ({@link ResponseEntity}, carte JSON du contrat). La composition de la
 * réponse (lectures des fragments, construction de l'entrée du moteur, appel du moteur, traduction des
 * types des silos) est portée par le cas d'usage {@link TaxUseCase} ({@code application-api}), implémenté
 * par {@code DefaultTaxUseCase}.
 *
 * <p>La mise à jour générique d'un champ de paramètres ({@code action: "updateSettings"} ou présence de
 * {@code field}) n'appartient pas au cas d'usage Fiscalité : elle reste routée directement vers
 * {@link SettingsCommandRouter}, comme avant R-80 (même mécanisme que {@code ParametersServiceImpl},
 * hors périmètre de ce patch, voir R-88).
 *
 * <p>RF-B01 (doc/architecture/13-persistance.md) : plus d'appel direct à {@code PersistenceManager}.
 */
@RestController
public class ImpotsServiceImpl implements ImpotsApi {

    private final TaxMapper taxMapper;
    private final TaxUseCase taxUseCase;
    private final SettingsCommandRouter settingsCommandRouter;

    public ImpotsServiceImpl(
            TaxMapper taxMapper,
            TaxUseCase taxUseCase,
            SettingsCommandRouter settingsCommandRouter) {
        this.taxMapper = taxMapper;
        this.taxUseCase = taxUseCase;
        this.settingsCommandRouter = settingsCommandRouter;
    }

    @Override
    public ResponseEntity<Object> getImpots() {
        TaxResultModel resultModel = taxUseCase.getImpots();
        Map<String, Object> response = taxMapper.toResponseMap(resultModel);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveImpotsConfig(Object body) {
        if (body instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;

            if (map.containsKey("action")) {
                String action = String.valueOf(map.get("action"));
                if ("resetDefaultTaxBrackets".equalsIgnoreCase(action)) {
                    taxUseCase.resetDefaultTaxBrackets();
                    return ResponseEntity.ok().build();
                } else if ("updateSettings".equalsIgnoreCase(action) || map.containsKey("field")) {
                    String field = String.valueOf(map.get("field"));
                    Object value = map.get("value");
                    settingsCommandRouter.updateSetting(field, value);
                    return ResponseEntity.ok().build();
                }
            }

            taxUseCase.saveTaxConfig(taxMapper.toSaveCommandFromMap(map));
        }

        return ResponseEntity.ok().build();
    }
}
