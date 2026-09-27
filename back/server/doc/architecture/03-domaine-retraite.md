# 03 — Retraite

Statut : 🟡 à valider — premier candidat de mise en œuvre

## Constat vérifié dans le code

Le calcul de projection retraite est dupliqué dans trois classes qui n'ont pas de moteur commun :

```text
RetraiteServiceImpl.computeRetirementProjection
OverviewCalculationService.computeRetirementProjection
TresorerieCalculationService.computeRetirementProjection
```

Une logique analogue existe également côté frontend. Créer uniquement un `RetirementInput` sans
centraliser le moteur ne résoudrait que la moitié du problème : les trois implémentations
resteraient susceptibles de diverger silencieusement (c'est déjà arrivé une fois entre Overview et
Patrimoine sur un autre calcul).

## Architecture cible

```text
                     ┌──────────────────────┐
                     │ RetirementInput      │
                     └─────────┬────────────┘
                               ▼
                ┌──────────────────────────────┐
                │ RetirementCalculationService │
                └──────────────┬───────────────┘
                               ▼
                    RetirementProjection
                      ▲       ▲        ▲
                      │       │        │
                 Retraite  Trésorerie  Overview
```

`RetirementCalculationService` devient l'unique propriétaire du calcul de projection retraite
backend. Trésorerie et Overview cessent de recalculer une retraite complète : ils demandent la
projection au service retraite et la transforment pour leurs propres besoins.

## Contrat d'entrée

```java
public record RetirementCalculationInput(
    int retireYear,
    RetirementParameters parameters,
    int eligibleChildrenCount,
    List<RetirementPersonInput> people
) {}

public record RetirementParameters(
    BigDecimal pass2026,
    BigDecimal passGrowthRate,
    BigDecimal agircPointValue,
    LocalDate agircPointDate,
    BigDecimal agircPointGrowthRate
) {}

public record RetirementPersonInput(
    int birthYear,
    int trimestresValides,
    LocalDate trimestresDate,
    List<SalaryHistoryEntry> salaryHistory,
    BigDecimal agircPoints,
    BigDecimal ratioPointsParEuro,
    List<AnnualSalaryProjection> projectedSalaries
) {}

public record SalaryHistoryEntry(int year, BigDecimal salary) {}
public record AnnualSalaryProjection(int year, BigDecimal annualSalary) {}
```

Sources exactes (voir aussi [12-settings.md](12-settings.md)) :

- `SimulationSettings.birthYear`, `retireAge` ;
- nombre d'enfants éligibles, déduit du domaine Budget de base / foyer fiscal, pas de la liste
  complète des `TaxChildModel` ;
- `RetirementModel.parameters` ;
- par personne : `birthYear`, `trimestresValides`, `trimestresDate`, `salaryHistory`,
  `agircPoints`, `ratioPointsParEuro` ;
- projection salariale annuelle, construite en amont (voir ci-dessous), pas le catalogue de
  revenus brut.

Ne doit **pas** recevoir : `BudgetDataModel`, `ChargeModel`, `PlacementModel`, `BankImportModel`,
`OneOffExpenseModel`, `TransferModel`.

## Ce qui sort volontairement du calcul

Utiles pour l'API/l'écran mais pas pour la mathématique : `person.id`, `person.name`,
`person.cadre`, `person.incomeLabel`. Ils restent dans un modèle de présentation ou une projection
d'application autour du calcul, jamais dans l'`Input`.

## Suppression du couplage `incomeLabel → IncomeModel`

Couplage actuel :

```text
RetirementPersonModel.incomeLabel → recherche → IncomeModel.label
  → monthly/start/end/growthRate → projection annuelle
```

C'est une responsabilité de composition, pas de calcul retraite. La factory produit directement
`AnnualSalaryProjection(year, annualSalary)` ; le moteur retraite n'a alors plus connaissance du
catalogue de revenus du budget.

## Effet sur les trois services consommateurs

- **Retraite** : `BudgetDataModel → RetirementInputFactory → RetirementCalculationService →
  RetraiteResultModel/DTO`.
- **Trésorerie** : ne recalcule plus une retraite complète ; demande la projection au service
  retraite et la transforme en flux annuel/mensuel.
- **Overview** : même principe, consomme la projection et l'agrège.
