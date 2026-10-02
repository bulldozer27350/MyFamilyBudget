package com.moe.myfamilybudget.server.internal.command;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.port.EconomicAssumptionsWriter;

/**
 * Owner applicatif de la notion Hypothèses économiques (SET-020, voir doc/architecture/12-settings.md) :
 * taux d'inflation ({@code inflationRate}). Ce n'est pas un domaine Settings global : une seule propriété.
 */
@Service
public class EconomicAssumptionsCommandService {

    private final EconomicAssumptionsWriter writer;

    public EconomicAssumptionsCommandService(EconomicAssumptionsWriter writer) {
        this.writer = writer;
    }

    public void updateInflationRate(Object value) {
        writer.updateInflationRate(value);
    }
}
