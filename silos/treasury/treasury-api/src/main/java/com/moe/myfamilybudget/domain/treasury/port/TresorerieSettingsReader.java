package com.moe.myfamilybudget.domain.treasury.port;

import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

/**
 * Port de lecture des paramètres généraux dont Trésorerie est propriétaire (SILO-100). Il remplace la lecture
 * de ces champs dans {@code SettingsReader}. Jamais {@code null}.
 */
public interface TresorerieSettingsReader {

    TresorerieSettingsModel getTresorerieSettings();
}
