package com.moe.myfamilybudget.server.internal.port;

import com.moe.myfamilybudget.server.internal.model.SettingsModel;

/**
 * Port de lecture pour le domaine Parametres (RF-B00).
 */
public interface SettingsReader {

    SettingsModel getSettings();
}