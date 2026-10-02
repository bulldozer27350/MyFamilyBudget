package com.moe.myfamilybudget.server.internal.command;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.port.RetirementSettingField;
import com.moe.myfamilybudget.server.internal.port.TaxSettingField;
import com.moe.myfamilybudget.server.internal.port.TresorerieSettingField;

/**
 * Table de routage unique des paramètres de {@code /settings} vers leur owner (SET-020), partagée par
 * {@code PATCH /settings} et par le second point d'entrée {@code saveImpotsConfig}. Remplace la logique
 * « tout ce qui n'est pas Objectifs va vers Fiscalité ».
 *
 * <p>Ce service n'ouvre aucune transaction : l'atomicité multi-domaines d'un {@code PATCH} portant plusieurs
 * familles reste portée par la façade appelante ({@code ParametersServiceImpl}, verrou du budget pris en
 * premier, voir VT-350b). Un champ inconnu (ou {@code null}) est ignoré sans erreur, comme avant.
 *
 * <p>Ce n'est pas un modèle global de paramètres : il ne détient aucune donnée, il choisit l'owner.
 */
@Service
public class SettingsCommandRouter {

    static final String SIMULATE_UNTIL_AGE = "simulateUntilAge";
    static final String INFLATION_RATE = "inflationRate";

    private final ObjectifsSettingsService objectifsSettingsService;
    private final TaxCommandService taxCommandService;
    private final RetirementCommandService retirementCommandService;
    private final TresorerieCommandService tresorerieCommandService;
    private final SimulationSettingsCommandService simulationSettingsCommandService;
    private final EconomicAssumptionsCommandService economicAssumptionsCommandService;

    public SettingsCommandRouter(
            ObjectifsSettingsService objectifsSettingsService,
            TaxCommandService taxCommandService,
            RetirementCommandService retirementCommandService,
            TresorerieCommandService tresorerieCommandService,
            SimulationSettingsCommandService simulationSettingsCommandService,
            EconomicAssumptionsCommandService economicAssumptionsCommandService) {
        this.objectifsSettingsService = objectifsSettingsService;
        this.taxCommandService = taxCommandService;
        this.retirementCommandService = retirementCommandService;
        this.tresorerieCommandService = tresorerieCommandService;
        this.simulationSettingsCommandService = simulationSettingsCommandService;
        this.economicAssumptionsCommandService = economicAssumptionsCommandService;
    }

    /** Owner du paramètre {@code field}, ou vide si aucun owner ne le possède. */
    public static Optional<SettingsOwner> ownerOf(String field) {
        if (field == null) {
            return Optional.empty();
        }
        if (ObjectifsSettingsService.owns(field)) {
            return Optional.of(SettingsOwner.OBJECTIFS);
        }
        if (RetirementSettingField.find(field).isPresent()) {
            return Optional.of(SettingsOwner.RETRAITE);
        }
        if (TresorerieSettingField.find(field).isPresent()) {
            return Optional.of(SettingsOwner.TRESORERIE);
        }
        if (SIMULATE_UNTIL_AGE.equals(field)) {
            return Optional.of(SettingsOwner.SIMULATION);
        }
        if (INFLATION_RATE.equals(field)) {
            return Optional.of(SettingsOwner.HYPOTHESES_ECONOMIQUES);
        }
        if (TaxSettingField.find(field).isPresent()) {
            return Optional.of(SettingsOwner.FISCALITE);
        }
        return Optional.empty();
    }

    /** Écrit {@code value} dans le paramètre {@code field} via son owner ; sans effet si le champ est inconnu. */
    public void updateSetting(String field, Object value) {
        Optional<SettingsOwner> owner = ownerOf(field);
        if (owner.isEmpty()) {
            return;
        }
        switch (owner.get()) {
            case OBJECTIFS -> objectifsSettingsService.updateField(field, value);
            case RETRAITE -> RetirementSettingField.find(field)
                    .ifPresent(f -> retirementCommandService.updateRetirementSetting(f, value));
            case TRESORERIE -> TresorerieSettingField.find(field)
                    .ifPresent(f -> tresorerieCommandService.updateTresorerieSetting(f, value));
            case SIMULATION -> simulationSettingsCommandService.updateSimulateUntilAge(value);
            case HYPOTHESES_ECONOMIQUES -> economicAssumptionsCommandService.updateInflationRate(value);
            case FISCALITE -> TaxSettingField.find(field)
                    .ifPresent(f -> taxCommandService.updateTaxSettings(f, value));
        }
    }
}
