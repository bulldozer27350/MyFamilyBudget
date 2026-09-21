package com.moe.myfamilybudget.server.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.server.internal.persistence.entity.LoanEntity;

/**
 * Les informations du contrat bancaire d'un pret (capital emprunte, nombre d'echeances, fin de la
 * mensualite lissee) doivent traverser la conversion modele <-> entite sans perte.
 */
class LoanContractInfoConversionTest {

    @Test
    void contractInfoSurvivesModelEntityRoundTrip() {
        LoanModel model = new LoanModel("loan-1", "Pret B", new BigDecimal("170000"), new BigDecimal("0.0225"),
                new BigDecimal("825"), new BigDecimal("25"), "2026-08-05", "2046-01-05",
                new BigDecimal("180000"), 240, "2036-01-05");

        LoanEntity entity = EntityModelConverter.toEntity(model, null);
        assertEquals(0, new BigDecimal("180000").compareTo(entity.getInitialAmount()));
        assertEquals(Integer.valueOf(240), entity.getTotalInstallments());
        assertEquals("2036-01-05", entity.getStepDate());

        LoanModel back = EntityModelConverter.toModel(entity);
        assertEquals(model, back);
    }

    @Test
    void historicConstructorLeavesContractInfoAbsent() {
        LoanModel model = new LoanModel("loan-2", "Pret A", new BigDecimal("50000"), new BigDecimal("0.01"),
                new BigDecimal("500"), BigDecimal.ZERO, "2026-01-01", "2036-01-01");

        assertNull(model.initialAmount());
        assertNull(model.totalInstallments());
        assertNull(model.stepDate());

        LoanModel back = EntityModelConverter.toModel(EntityModelConverter.toEntity(model, null));
        assertEquals(model, back);
    }
}
