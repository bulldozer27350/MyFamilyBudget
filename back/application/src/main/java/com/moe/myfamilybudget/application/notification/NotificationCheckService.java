package com.moe.myfamilybudget.application.notification;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.factory.NotificationInputFactory;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationDispatchService;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationDispatchService.RuleInputs;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Contrôle des notifications, côté application (SILO-180) : assemble l'entrée de chaque règle à partir des
 * fragments lus via les ports de lecture des autres silos ({@link BankReader}, {@link TresorerieSettingsReader},
 * {@link GoalReader}, {@link PatrimoineReader}) et de {@link NotificationInputFactory}, puis délègue le
 * contrôle au silo Notifications ({@link NotificationDispatchService}). Le silo ne connaît ainsi aucun autre
 * silo (D1).
 *
 * <p>Depuis NOTIF-010, les lectures ne sont effectuées que pour les règles actives : chaque entrée est une
 * fonction des paramètres de notification, appelée par le silo uniquement si la règle est active.
 */
@Service
public class NotificationCheckService {

    private final NotificationDispatchService dispatchService;
    private final BankReader bankReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final GoalReader goalReader;
    private final PatrimoineReader patrimoineReader;

    public NotificationCheckService(NotificationDispatchService dispatchService, BankReader bankReader,
            TresorerieSettingsReader tresorerieSettingsReader, GoalReader goalReader,
            PatrimoineReader patrimoineReader) {
        this.dispatchService = dispatchService;
        this.bankReader = bankReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.goalReader = goalReader;
        this.patrimoineReader = patrimoineReader;
    }

    /** Contrôle déclenché après une mutation du budget. Retourne le nombre d'alertes envoyées. */
    public int runAutomaticCheck() {
        return dispatchService.runAutomaticCheck(ruleInputs());
    }

    /** Contrôle manuel (bouton « Vérifier »). Retourne le nombre d'alertes envoyées. */
    public int runManualCheck() {
        return dispatchService.runManualCheck(ruleInputs());
    }

    private RuleInputs ruleInputs() {
        return new RuleInputs(
                settings -> NotificationInputFactory.debitThreshold(
                        bankReader.getBankImport(), settings.debitThresholdAmount()),
                settings -> NotificationInputFactory.balanceFloor(
                        bankReader.getBankImport(), openingBalance(), settings.balanceFloorAmount()),
                settings -> NotificationInputFactory.objectifReachable(
                        goalReader.getGoals(), patrimoineReader.getPlacements()));
    }

    /** Solde de départ des paramètres budgétaires ({@code null} traité comme zéro par la factory). */
    private BigDecimal openingBalance() {
        TresorerieSettingsModel treasurySettings = tresorerieSettingsReader.getTresorerieSettings();
        return treasurySettings != null ? treasurySettings.getEffectiveStartBalance() : null;
    }
}
