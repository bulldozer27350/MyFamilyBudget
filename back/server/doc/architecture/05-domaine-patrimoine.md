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

## Point ouvert (tranché en RF-400) {#point-ouvert}

Le catalogue d'Inputs du document source mentionnait un `ContributionDecisionPlan` en entrée de
`PatrimoineProjectionInput`, ce qui aurait introduit une dépendance retour Trésorerie/mécanisme de
sweep → Patrimoine, en plus de la dépendance Patrimoine → Trésorerie (cash-flow de placement
consommé par `TreasuryProjectionInput`, voir [06-domaine-tresorerie.md](06-domaine-tresorerie.md)).
[00-principes.md](00-principes.md) liste explicitement `Patrimoine ↔ Trésorerie` parmi les cycles
interdits et prescrit « la projection unidirectionnelle » : **RF-400 tranche donc en faveur de
l'option retenue par RF-301** (`sweepPriority`, `sweepCap`, `pauseTriggerBalance`, `pausePriority`
restent des valeurs de configuration statiques, jamais une décision calculée par Trésorerie).
Aucun `ContributionDecisionPlan` n'est introduit ; Trésorerie et Patrimoine approximent chacun
l'autre indépendamment (voir la section correspondante de 06-domaine-tresorerie.md) plutôt que de
se référencer.

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
