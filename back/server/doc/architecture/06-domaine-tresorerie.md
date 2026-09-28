# 06 — Trésorerie

Statut : 🟢 validé (RF-400)

## Constat

`TresorerieCalculationService` est déjà extrait du contrôleur, mais reçoit encore directement
`BudgetDataModel` : `computeTresorerie(BudgetDataModel data, boolean useConstantEuros)`.
L'extraction hors contrôleur était nécessaire mais ne constitue pas encore la frontière de
domaine finale.

## Le bon découpage n'est pas un Input unique

Un modèle d'entrée unique deviendrait rapidement un `BudgetDataModel` bis, vu le nombre de
familles consultées aujourd'hui (settings, revenus, charges, oneoff, variables, placements,
virements, fiscalité, retraite, import bancaire). Le principe à retenir : Trésorerie reçoit **les
résultats/projections déjà calculés des autres domaines**, jamais leurs modèles source.

```java
public record TreasuryProjectionInput(
    TreasurySimulationPeriod period,
    List<IncomeProjectionInput> incomes,
    List<ChargeProjectionInput> charges,
    List<VariableIncomeProjection> variableIncomes,
    List<OneOffCashflow> oneOffExpenses,
    List<TransferProjection> transfers,
    List<PlacementCashflowInput> placements,
    TaxProjection taxProjection,
    RetirementIncomeProjection retirementIncome,
    TreasuryParameters parameters
) {}
```

Implémenté tel quel en RF-400 (`internal.calculation`, voir la liste des fichiers en fin de
section) ; `incomes`/`charges`/`variableIncomes`/`oneOffExpenses`/`transfers` sont des projections
minimales du budget de base (ni `id`, ni `categoryId`, ni `notes`), `taxProjection` et
`retirementIncome` sont déjà calculés par les domaines Fiscalité (RF-203) et Retraite (RF-101), pas
recalculés une deuxième fois. `TreasuryInputFactory` (purement additive, non branchée jusqu'à
RF-401) les compose à partir de `BudgetDataModel`.

```

```text
Fiscalité ────────→ TaxProjection
Retraite ─────────→ RetirementIncomeProjection
Patrimoine ───────→ (aucune, voir décision ci-dessous)
Banque ───────────→ (hors contrat, computeRealAverages/buildTresorerieSuggestions restent
                      à part — non traité par RF-400, voir points restants en fin de section)
                         ↓
                 TreasuryCalculationService
```

**Point ouvert tranché (RF-400).** 00-principes.md liste explicitement `Patrimoine ↔ Trésorerie`
parmi les cycles interdits et prescrit, pour ce cas, « la projection unidirectionnelle ». Aucun
`ContributionDecisionPlan` n'est donc introduit en sortie de Trésorerie : `placements` (voir
`PlacementCashflowInput`) reste une somme simple des versements configurés sur chaque placement,
sans tenir compte du mécanisme de pause du domaine Patrimoine — exactement le comportement actuel
de `placementsMonthlyAnnualForYear`. Patrimoine garde de son côté sa propre approximation de la
trésorerie pour sa décision de pause (voir `PatrimoineProjectionParameters`, RF-301) : les deux
domaines approximent chacun l'autre indépendamment plutôt que de se référencer, ce qui évite le
cycle sans changer aucun résultat observable.

## Fonctions de calcul unitaires déjà publiques

`chargeMonthlyForYear`, `chargeAnnualForYear`, `incomeMonthlyForYear`, `incomeAnnualForYear` sont
de bons candidats pour devenir de vraies fonctions de domaine prenant uniquement leur modèle
minimal — elles ne doivent pas recevoir `BudgetDataModel`.

## Conclusion

```text
BudgetDataModel
    ↓ application/composition (TreasuryInputFactory)
    ├── impôt déjà projeté (TaxInputFactory + TaxCalculator)
    ├── pension déjà projetée (RetirementInputFactory + RetirementCalculationService)
    ├── versements vers placements (somme simple, sans pause — décision ci-dessus)
    └── lignes revenu/charge/variable/ponctuel/virement normalisées
             ↓
      TreasuryProjectionInput
             ↓
      TreasuryCalculationService (RF-401 : branchement, pas encore fait)
```

Trésorerie devient un agrégateur de **projections**, pas un agrégateur de modèles de persistance.

## Fichiers RF-400

`internal.calculation` : `TreasuryProjectionInput`, `TreasurySimulationPeriod`,
`IncomeProjectionInput`, `ChargeProjectionInput`, `VariableIncomeProjection` (+ `Override`
imbriqué), `OneOffCashflow`, `TransferProjection`, `PlacementCashflowInput`, `TaxProjection` (+
`Withholding` imbriqué), `RetirementIncomeProjection` (+ `AnnualPension` imbriqué),
`TreasuryParameters`. `internal.factory` : `TreasuryInputFactory` (purement additive).

## Restant hors RF-400

`computeRealAverages`, `buildCategoryOptions` et `buildTresorerieSuggestions` (moyennes réelles du
pointage bancaire, suggestions budgétaires) ne sont pas couverts par `TreasuryProjectionInput` : ce
sont des besoins distincts de la projection de flux, non mentionnés dans le contrat recommandé par
ce document. À traiter explicitement en RF-401 (branchement) plutôt que supposé résolu par ce
patch.
