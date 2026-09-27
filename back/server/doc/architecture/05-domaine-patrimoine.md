# 05 — Patrimoine

Statut : 🟡 à valider

## Constat

`PatrimoineServiceImpl` cumule aujourd'hui plusieurs responsabilités : contrôleur REST,
orchestrateur de lecture/persistance, moteur de projection patrimoniale, moteur de chronologie
d'un placement, mécanisme de pause automatique des versements, application du déflateur
« euros constants ». Le découpage en `XxxInput` s'applique, mais il faut d'abord **séparer
plusieurs responsabilités métier** avant de fabriquer un contrat — un `PatrimoineCalculationInput`
unique et massif serait un `BudgetDataModel` bis.

## Contrat recommandé (premier niveau)

```java
public record PatrimoineProjectionInput(
    List<PlacementProjectionInput> placements,
    List<PlacementTransfer> transfers,
    CashflowProjection cashflow,
    PatrimoineProjectionParameters parameters
) {}

public record PlacementProjectionInput(
    String id,
    String label,
    BigDecimal initialBalance,
    LocalDate balanceDate,
    BigDecimal monthlyContribution,
    YearMonth contributionFrom,
    YearMonth contributionUntil,
    BigDecimal pessimisticRate,
    BigDecimal expectedRate,
    BigDecimal optimisticRate,
    boolean excludedFromRetirement,
    Integer sweepPriority,
    BigDecimal sweepCap,
    BigDecimal pauseTriggerBalance,
    Integer pausePriority
) {}
```

La liste `history` (historique de valorisation) ne doit être incluse que dans l'input du graphe
historique d'un placement, pas dans la projection patrimoniale globale.

## Deux concepts métier à séparer explicitement

`PatrimoineServiceImpl` simule aujourd'hui en arrière-plan **tous les placements** pour déterminer
si le placement visualisé doit être mis en pause — signe qu'il existe déjà deux concepts
distincts qui ne devraient pas partager `PlacementModel` par défaut :

```text
Projection patrimoniale
        +
Moteur de règles d'allocation / pause / sweep
```

## Point ouvert {#point-ouvert}

Le catalogue d'Inputs du document source mentionne un `ContributionDecisionPlan` en entrée de
`PatrimoineProjectionInput`, ce qui suggère une dépendance retour Trésorerie/mécanisme de sweep →
Patrimoine, en plus de la dépendance Patrimoine → Trésorerie (cash-flow de placement consommé par
`TreasuryProjectionInput`, voir [06-domaine-tresorerie.md](06-domaine-tresorerie.md)). Le document
source ne tranche pas explicitement comment ces deux flux coexistent sans former le cycle
Patrimoine ↔ Trésorerie que [00-principes.md](00-principes.md) interdit.

**Ce point doit être tranché avant l'étape 5 (Trésorerie), pas supposé résolu.** Proposition de
travail : traiter l'étape 4 (Patrimoine) en se limitant à une projection basée sur les paramètres
déjà stockés par placement (`sweepPriority`, `sweepCap`, `pauseTriggerBalance`, `pausePriority` —
des valeurs de configuration statiques, pas une décision calculée), et ne reprendre la notion de
`ContributionDecisionPlan` qu'au moment de l'étape 5, une fois que Trésorerie a un contrat stable
capable de la produire en sortie.

## Conclusion

Pas de `PatrimoineCalculationInput` unique. Assemblage de projections métier :

```text
PlacementModel / SettingsModel / cashflows / transfers
                  ↓ normalisation / projection
     PlacementProjectionInput / CashflowProjection / PatrimoineProjectionParameters
                  ↓
       moteurs patrimoniaux
```

Le contrôleur REST devient une façade mince au-dessus de ces moteurs.
