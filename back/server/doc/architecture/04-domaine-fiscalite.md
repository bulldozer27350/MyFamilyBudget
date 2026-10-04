# 04 — Fiscalité

Statut : 🟡 à valider

## Constat

`TaxCalculator.computeTaxResult(BudgetDataModel)` est statique et reçoit le modèle global.
L'analyse détaillée montre que l'essentiel de la surface de couplage vient d'une seule fonction
technique, `findEarliestYear(BudgetDataModel)`, qui parcourt `incomes`, `charges`, `placements`,
`oneoff`, `transfers`, `bankImport` et `settings.pivotDate` juste pour déduire un horizon
temporel. C'est un couplage artificiel : le calcul fiscal peut recevoir explicitement sa période
de simulation au lieu de la redéduire depuis le modèle global.

Deuxième couplage important : `TaxCalculator` contient encore une logique de projection de
pension (il reconstruit lui-même un revenu de pension à partir de `RetirementModel` /
`RetirementPersonModel` / `IncomeModel`). Cela mélange deux responsabilités distinctes
(Retraite : combien de pension ? / Fiscalité : quel impôt dessus ?). C'est la réduction de
couplage la plus importante de ce domaine, et elle dépend de l'étape 2 (Retraite) déjà réalisée.

## Contrat d'entrée

```java
public record TaxCalculationInput(
    TaxSimulationPeriod period,
    TaxHouseholdParameters household,
    List<AnnualTaxIncome> incomes,
    List<AnnualVariableIncome> variableIncomes,
    List<Integer> childBirthYears,
    List<TaxBracket> brackets,
    List<TaxRateOverride> rateOverrides,
    List<TaxActualOverride> actualOverrides,
    List<TaxablePensionIncome> retirementIncome
) {}

public record TaxSimulationPeriod(int startYear, int endYear) {}
public record AnnualTaxIncome(int year, BigDecimal amount) {}

public record TaxHouseholdParameters(
    int birthYear,
    int retireAge,
    int childExitAge,
    BigDecimal taxAbattement
) {}
```

Le nom exact des types peut évoluer ; le principe à retenir est que le moteur reçoit des montants
**déjà projetés et déjà sémantisés**, jamais les modèles source (`IncomeModel`, `TransferModel`,
etc.).

Chaîne cible pour la pension imposable :

```text
RetirementCalculationService → TaxablePensionIncome → TaxCalculationService
```

SILO-130 (décision D1) : `TaxablePensionIncome` appartient au domaine Fiscalité ; `TaxInputFactory` (application) traduit la projection de
`RetirementCalculationService` et `domain-tax` ne dépend plus de `domain-retirement`.

## Dépendances qui doivent disparaître de `TaxCalculationInput`

Après séparation de l'horizon et de la pension : `charges`, `placements`, `oneoff`, `transfers`,
`bankImport`, `settings.pivotDate`, `settings.inflationRate`. Leur présence actuelle est
principalement liée à `findEarliestYear` ou à la projection de pension — à vérifier au cas par
cas pendant le refactoring, pas supposée acquise à l'avance.

## Migration en deux étapes logiques

1. `BudgetDataModel → TaxCalculationInput → TaxCalculator` (encapsulation de frontière, gain
   immédiat sur la testabilité).
2. Sortie de la projection retraite + sortie de `findEarliestYear` → `TaxCalculationInput`
   nettement plus petit. C'est cette deuxième étape qui fait réellement disparaître le couplage.
