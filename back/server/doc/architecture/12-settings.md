# 12 — Settings : façade REST unique, propriété métier distribuée

Statut : 🟡 à valider

## Décision retenue

La cible n'est **pas** un `GlobalSettingsModel` qui recréerait un `BudgetDataModel` miniature.

```text
                         HTTP / OpenAPI
                              │
                              ▼
                   SettingsFacade / Orchestrator
                              │
             ┌────────────────┼─────────────────┐
             │                │                 │
             ▼                ▼                 ▼
       TaxSettings      RetirementSettings   TreasurySettings
             │                │                 │
             ▼                ▼                 ▼
        Tax service       Retirement service  Treasury service
             │                │                 │
             └────────────── persistence / repositories ────┘

       + éventuellement ProjectionSettings / EconomicAssumptions
         pour les paramètres réellement communs à plusieurs moteurs.
```

La façade REST reste unique côté HTTP (`GET`/`PATCH /settings`, contrat actuel conservé). Il
n'existe en revanche plus de modèle métier global `SettingsModel` regroupant tous les paramètres.
La façade reçoit une modification, identifie les domaines concernés, construit les commandes
métier appropriées et appelle les services correspondants.

| Niveau | Responsabilité | Modèle global autorisé ? |
|---|---|---|
| REST | Facade de lecture/écriture des paramètres | Oui, DTO d'API composite |
| Application/orchestration | Décider quels domaines appeler | Oui, `UpdateSettingsCommand` composite court |
| Métier | Porter et valider les paramètres du domaine | **Non** |
| Persistance | Stocker les paramètres du domaine | **Non** |

Le « settings global » peut donc exister comme forme de transport/orchestration, jamais comme
agrégat métier partagé par tous les domaines. C'est le compromis qui garde un seul endpoint REST
sans recréer le couplage qu'on cherche à supprimer.

## Répartition cible

| Champ actuel de `SettingsModel` | Propriétaire cible | Consommateurs principaux |
|---|---|---|
| `birthYear` | Retraite | Retraite, Trésorerie, Overview |
| `retireAge` | Retraite | Retraite, Trésorerie, Overview |
| `simulateUntilAge` | Simulation | Trésorerie, Overview |
| `inflationRate` | Hypothèses économiques | Trésorerie, Patrimoine, Overview |
| `pivotDate` | Trésorerie | Trésorerie, Overview |
| `pivotMode` | Trésorerie | Trésorerie, Overview |
| `startBalance` | Trésorerie | Trésorerie, Notifications, Overview, Patrimoine |
| `childExitAge` | Fiscalité | Fiscalité |
| `taxAbattement` | Fiscalité | Fiscalité |
| `pass2026` | Retraite | Retraite (dupliqué aujourd'hui avec `RetirementModel` — voir ci-dessous) |
| `passGrowthRate` | Retraite | idem |
| `sweepEnabled` | Trésorerie | Trésorerie, Patrimoine |
| `cashCeiling` | Trésorerie | Trésorerie, Patrimoine |
| `cashFloor` | Trésorerie | Trésorerie, Patrimoine |
| `cashAlertThreshold` | Trésorerie | Trésorerie, Patrimoine, notifications indirectement |
| `goalSecureHorizonMonths` | Objectifs | Objectifs |
| `goalLiquidHorizonMonths` | Objectifs | Objectifs |

## Cas particuliers

- **`pass2026` / `passGrowthRate`** : existent aujourd'hui à la fois dans `SettingsModel` et
  `RetirementModel`, mais les calculs observés utilisent la valeur de `RetirementModel`. Cible :
  ces valeurs (ainsi que `agircPointValue`, `agircPointDate`, `agircPointGrowthRate`)
  appartiennent exclusivement à `RetirementSettings`. Les DTO API qui les exposent encore comme
  settings globaux sont un héritage de contrat à nettoyer lors du découpage OpenAPI (étape 13).
- **`birthYear` / `retireAge`** : même si Overview/Trésorerie les utilisent pour un horizon, leur
  propriété métier reste Retraite. L'agrégateur reçoit un `RetirementHorizon(birthYear, retireAge,
  retireYear)` ou juste `retireYear`, jamais `SettingsModel`.
- **`simulateUntilAge`** : décrit la profondeur de simulation, pas le domaine retraite. Sort dans
  une petite notion `SimulationSettings.simulateUntilAge`, partageable par Trésorerie et Overview
  sans redevenir un modèle global.
- **`inflationRate`** : utilisé par plusieurs projections ; le dupliquer créerait une incohérence
  silencieuse. Porté dans une capacité transverse minuscule et stable : `EconomicAssumptions
  .inflationRate`.
