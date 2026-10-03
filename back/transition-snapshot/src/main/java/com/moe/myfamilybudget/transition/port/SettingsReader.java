package com.moe.myfamilybudget.transition.port;

import com.moe.myfamilybudget.transition.model.SettingsModel;

/**
 * Port de lecture pour le domaine Parametres (RF-B00).
 */
public interface SettingsReader {

    SettingsModel getSettings();
}