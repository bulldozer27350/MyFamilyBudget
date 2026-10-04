package com.moe.myfamilybudget.transition.port;

import com.moe.myfamilybudget.transition.model.EconomicAssumptionsModel;

/** Port de lecture de la notion Hypothèses économiques (SILO-100), symétrique de {@link EconomicAssumptionsWriter}. */
public interface EconomicAssumptionsReader {

    EconomicAssumptionsModel getEconomicAssumptions();
}
