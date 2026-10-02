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

## Ownership applicatif par famille (SET-010)

Cette section fige, pour les patchs `SET-020` à `SET-040`, qui possède chaque paramètre côté
application et où l'état actuel du code s'en écarte. Elle ne change ni l'URL REST ni le contrat
`GET`/`PATCH /settings` : seul le routage interne est concerné.

| Famille | Champs (clés du contrat actuel) | Owner applicatif cible | Port d'écriture existant | État actuel de l'écriture via `PATCH /settings` |
|---|---|---|---|---|
| Retraite | `birthYear`, `retireAge`, `pass2026`, `passGrowthRate` (les paramètres AGIRC sont déjà portés par `RetirementModel`) | `RetirementCommandService` | `RetirementWriter` (seulement `updateRetirement(RetirementModel)`, sans commande par champ) | Routé vers Fiscalité. `birthYear` et `retireAge` n'existent que dans `SettingsModel`. `pass2026` et `passGrowthRate` ne modifient que la copie de `SettingsModel`, pas celle de `RetirementModel` (voir `SET-040`). |
| Fiscalité | `childExitAge`, `taxAbattement` | `TaxCommandService` | `TaxWriter.updateTaxSettings` | Routage correct (seule famille légitimement traitée par Fiscalité), mais via la mutation générique `field`/`value` (voir `SET-030`). |
| Trésorerie | `pivotDate`, `pivotMode`, `startBalance` (alias `pivotBalanceManual`), `sweepEnabled`, `cashCeiling`, `cashFloor`, `cashAlertThreshold` | `TresorerieCommandService` | `TresorerieWriter` (lignes uniquement, aucune commande de paramètres) | Routé vers Fiscalité. Commande de paramètres à introduire. |
| Objectifs | `goalSecureHorizonMonths`, `goalLiquidHorizonMonths` | `ObjectifsSettingsService` | Table autonome (`ObjectifsSettingsStore`) | Déjà routé vers son owner par `ParametersServiceImpl`. Aucun écart. |
| Simulation | `simulateUntilAge` | `SimulationSettings` (petite notion dédiée, owner à créer) | aucun | Routé vers Fiscalité. |
| Hypothèses économiques | `inflationRate` | `EconomicAssumptions` (capacité transverse minuscule, owner à créer) | aucun | Routé vers Fiscalité. |

Le stockage physique reste aujourd'hui unique (`SettingsEntity` pour tout sauf les paramètres
Objectifs) : sa séparation relève des patchs `DB-xxx`, pas de `SET-*`. L'ownership applicatif
ci-dessus est indépendant de ce stockage.

### Champs encore traités comme « fiscaux/généraux »

Sur les 16 clés de `TaxSettingField`, **14** appartiennent à une autre famille que Fiscalité
(toutes sauf `childExitAge` et `taxAbattement`). Le raisonnement actuel est « tout ce qui n'est pas
Objectifs va vers Fiscalité », porté par :

- `ParametersServiceImpl.updateSetting` : branche `else` vers `TaxCommandService.updateTaxSettings` ;
- `TaxCommandService.updateTaxSettings`, `TaxWriter.updateTaxSettings` et `TaxSettingField`
  (nom et javadoc « paramètre fiscal ») ;
- `BudgetMutationService.updateTaxSettings` (« paramètre lié aux impôts ou généraux ») ;
- un **second point d'entrée** : `ImpotsServiceImpl.saveImpotsConfig` (action `updateSettings` ou
  présence de `field`) route aussi tous les champs vers Fiscalité, sans le routage Objectifs de
  `ParametersServiceImpl` : un champ Objectifs reçu par ce chemin est ignoré sans erreur.

### Règles pour la suite

- `SET-020` dispatche par propriété vers l'owner de ce tableau, pour `PATCH /settings` **et** pour le
  second point d'entrée `saveImpotsConfig` (même table de routage, pas deux copies).
- L'atomicité multi-domaines d'un `PATCH` portant plusieurs familles est conservée (verrou du budget
  pris en premier, voir `VT-350b`) ; la lecture composite de `GET /settings` reste inchangée.
- `SET-030` supprime la mutation générique `updateTaxSettings(field, value)` une fois chaque famille
  branchée ; `SET-040` tranche la double écriture `pass2026` / `passGrowthRate` en faveur de Retraite.
- Tant qu'un owner n'a pas de port d'écriture dédié (Retraite par champ, Trésorerie, Simulation,
  Hypothèses économiques), `SET-020` introduit la commande minimale nécessaire, sans créer de modèle
  global de paramètres.
