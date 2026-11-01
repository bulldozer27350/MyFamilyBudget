package com.moe.myfamilybudget.domain.tax.port;

import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;

/**
 * Port de lecture des paramètres généraux dont Fiscalité est propriétaire (SILO-100). Il remplace la lecture de
 * ces champs dans {@code SettingsReader}. Jamais {@code null}.
 */
public interface TaxSettingsReader {

    TaxSettingsModel getTaxSettings();
}
