package com.moe.myfamilybudget.domain.retirement.port;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;

/**
 * Port de lecture des paramètres généraux dont Retraite est propriétaire (SILO-100). Il remplace la lecture de
 * ces champs dans {@code SettingsReader}. Jamais {@code null} : les valeurs absentes sont des champs
 * {@code null} du modèle, résolus par ses accesseurs {@code getEffective*}.
 */
public interface RetirementSettingsReader {

    RetirementSettingsModel getRetirementSettings();
}
