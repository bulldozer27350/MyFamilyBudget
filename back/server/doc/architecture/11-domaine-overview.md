# 11 — Overview

Statut : 🟡 à valider

## Constat

`OverviewCalculationService` a été extrait de `OverviewServiceImpl`, mais reste un agrégateur
transversal extrêmement large (Trésorerie, Patrimoine, Immobilier, Retraite, Fiscalité,
Placement, pivot bancaire) et contient encore sa propre implémentation de projection retraite —
troisième confirmation, avec les deux autres services cités dans
[03-domaine-retraite.md](03-domaine-retraite.md), qu'un moteur retraite unique est nécessaire.

## Cible

```text
                    ┌─ TreasuryProjection
                    ├─ PatrimoineProjection
OverviewInput  ←────┼─ RetirementProjection
                    ├─ TaxProjection
                    └─ RealEstateProjection
```

```java
OverviewResultModel computeOverview(OverviewInput input)
```

La responsabilité d'`OverviewCalculationService` devient uniquement la composition et la
transformation de projections déjà calculées par les domaines. Il ne doit contenir aucun
`RetirementModel` ni aucune logique de recalcul — uniquement des projections déjà produites.

Comme pour Analyse, `OverviewResultModel.data` (aujourd'hui un `BudgetDataModel`) est une fuite
par le résultat à supprimer (voir [00-principes.md](00-principes.md)).

## Indicateur de fin de chantier

Le découpage sera suffisamment avancé quand ces signatures seront possibles, et que plus aucune
d'entre elles ne prendra `BudgetDataModel` :

```text
Retraite:    compute(RetirementCalculationInput)
Fiscalité:   compute(TaxCalculationInput)
Patrimoine:  compute(PatrimoineProjectionInput)
Trésorerie:  compute(TreasuryProjectionInput)
Pointage:    compute(PointageInput)
Analyse:     compute(AnalyseInput)
Overview:    compute(OverviewInput)
```

La présence résiduelle de `BudgetDataModel` dans un contrôleur, un mapper ou une factory de
composition reste normale. Sa présence dans un moteur métier signale un couplage encore à
traiter.
