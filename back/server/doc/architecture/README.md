# Architecture cible — sortir `BudgetDataModel` du chemin métier

Ce dossier consolide, sous forme validable domaine par domaine, la réflexion menée sur la
décomposition de `BudgetDataModel`. Il remplace le document de travail unique
(`MyFamilyBudget-budget-data-model-matrix.md`, ~140 sections) qui a servi à l'explorer : ce
document reste la trace historique du raisonnement, mais **cette arborescence est la référence
à jour**.

## Pourquoi ce chantier

`BudgetDataModel` est aujourd'hui consommable par la quasi-totalité des services métier
(12 des 16 implémentations du package `internal/impl` le référencent directement, certaines
méthodes de calcul le prenant même en paramètre public — ex. `TresorerieServiceImpl
.computeTresorerie(BudgetDataModel data, ...)`). Conséquence :

- impossible d'écrire un test de composant sur un moteur de calcul sans construire (ou mocker)
  le modèle complet ;
- impossible de faire travailler plusieurs agents en parallèle sur des domaines différents sans
  risque de collision, faute de frontière compilée ;
- aucune observabilité fine par domaine (tout transite par le même objet).

**L'objectif de ce chantier n'est pas de préparer une architecture microservices.** L'application
reste mono-instance, mono-base, exploitée par un seul développeur. L'objectif est strictement :
rendre chaque domaine métier testable isolément et rendre les frontières entre domaines
vérifiables par le compilateur/la CI plutôt que par la seule discipline.

## Comment lire et valider ce dossier

Chaque fichier peut être relu et validé indépendamment. Un fichier validé peut passer en
implémentation (patch) sans attendre les autres. Statut affiché en tête de chaque fichier :

- 🟡 **à valider** — proposition issue de la consolidation, pas encore relue ligne à ligne.
- 🟢 **validé** — relu et approuvé, prêt à passer en patch.
- ✅ **implémenté** — le code correspond à ce document.

## Sommaire

| Fichier | Contenu | Statut |
|---|---|---|
| [00-principes.md](00-principes.md) | Règles interdites, test de conception, graphe de dépendances, anti-cycles | 🟡 |
| [01-sequencement.md](01-sequencement.md) | Ordre de migration retenu, garde-fous incrémentaux | 🟡 |
| [02-domaine-budget-de-base.md](02-domaine-budget-de-base.md) | Revenus/charges/oneoff/variables/virements — sous-domaine partagé | 🟡 |
| [03-domaine-retraite.md](03-domaine-retraite.md) | Retraite — centralisation du moteur (priorité 1) | 🟡 |
| [04-domaine-fiscalite.md](04-domaine-fiscalite.md) | Fiscalité | 🟡 |
| [05-domaine-patrimoine.md](05-domaine-patrimoine.md) | Patrimoine / placements / immobilier | 🟡 |
| [06-domaine-tresorerie.md](06-domaine-tresorerie.md) | Trésorerie | 🟡 |
| [07-domaine-banque-pointage.md](07-domaine-banque-pointage.md) | Import bancaire, opérations en cours, pointage | 🟡 |
| [08-domaine-analyse.md](08-domaine-analyse.md) | Analyse réel vs prévisionnel | 🟡 |
| [09-domaine-objectifs-notifications.md](09-domaine-objectifs-notifications.md) | Objectifs et notifications | 🟡 |
| [10-domaine-prets-suggestions.md](10-domaine-prets-suggestions.md) | Crédit / suggestions de taux | 🟡 |
| [11-domaine-overview.md](11-domaine-overview.md) | Overview — agrégateur pur | 🟡 |
| [12-settings.md](12-settings.md) | Découpage de `SettingsModel` par propriétaire | 🟡 |
| [13-persistance.md](13-persistance.md) | Persistance — ce qui est obligatoire vs optionnel avant Maven | 🟡 |
| [14-checklist-maven.md](14-checklist-maven.md) | Checklist de passage aux modules Maven | 🟡 |
| [15-backlog-patchs.md](15-backlog-patchs.md) | Liste ordonnée de patchs (avec statut et prérequis) à distribuer à des agents | 🟡 |
| [21-plan-silotage.md](21-plan-silotage.md) | Plan de travail du silotage complet : suppression de `BudgetDataModel`, persistance par silo, séparation application/web | 🟡 |

## Ce qui a changé par rapport au document source

La consolidation a tranché ou clarifié plusieurs points sur lesquels le document source se
contredisait ou restait ambigu (détails dans les fichiers concernés) :

1. **Place de la persistance dans la séquence** — le document source proposait deux ordres
   différents selon la section. Version retenue : décentraliser d'abord le métier
   (Inputs/Outputs), la persistance vient ensuite, en deux paliers de risque distincts (voir
   [13-persistance.md](13-persistance.md)).
2. **Garde-fous d'architecture (ArchUnit)** — le document source les plaçait en toute dernière
   étape, après 8 à 9 migrations de domaine faites sans aucune vérification automatique. Version
   retenue : un garde-fou minimal dès l'étape 1, étendu domaine par domaine au fur et à mesure
   (voir [01-sequencement.md](01-sequencement.md)).
3. **Patrimoine / Trésorerie** — un risque de cycle non résolu par le document source entre ces
   deux domaines est documenté explicitement comme point ouvert plutôt que masqué (voir
   [05-domaine-patrimoine.md](05-domaine-patrimoine.md#point-ouvert)).
