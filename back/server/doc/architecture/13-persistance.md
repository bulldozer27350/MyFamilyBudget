# 13 — Séparation de la persistance par domaine

Statut : 🟡 à valider

> **Mise à jour du 4 octobre 2026** : l'objectif a évolué (suppression totale de `BudgetDataModel`, persistance par silo, application ≠ web). O3, R3 et C1 ont été révisés par SILO-001 (versions d'origine remplacées ci-dessous) ; voir [`21-plan-silotage.md`](21-plan-silotage.md).

## Pourquoi ce fichier existe

La séparation Maven n'a de valeur que si les frontières logiques préparées dans le code correspondent à des
responsabilités techniques réellement séparables. La persistance est aujourd'hui le principal endroit où
`BudgetDataModel`, `BudgetDataEntity`, `PersistenceManager` et les repositories JPA recréent une dépendance
transverse.

Ce document décrit la cible et les contraintes du chantier. L'ordonnancement détaillé, sous forme de petits
patchs mergeables et parallélisables, est dans [`18-backlog-persistance-patchs.md`](18-backlog-persistance-patchs.md).

## Constat sur l'existant

La base n'est pas un simple blob JSON. Le modèle est déjà largement normalisé en JPA : seules les données de
`BankImportEntity` sont stockées sous forme de JSON texte. En revanche, `BudgetDataEntity` reste le **hub JPA** :
une vingtaine de relations `@OneToOne` / `@OneToMany` relient ce root à la plupart des entités métier.

La chaîne actuelle est encore proche de :

```text
REST / application
      ↓
PersistenceManager
      ↓
BudgetDataModel
      ↓
BudgetPersistenceGateway
      ↓
BudgetDataEntity + repositories
```

Des ports et services de commande existent déjà, mais plusieurs sont encore des façades de transition vers
`PersistenceManager`. Cette transition est utile : elle permet d'isoler les appelants avant de modifier le
schéma JPA.

## Finalité

À terme, la persistance doit permettre :

- à un domaine de lire ses propres données via un port explicite ;
- à un domaine de modifier ses propres données via des commandes orientées métier ;
- à l'implémentation JPA de changer sans exposer `BudgetDataEntity` aux calculs ;
- de retirer progressivement `BudgetDataEntity` comme hub relationnel ;
- de conserver les opérations globales (import/export/reset) derrière une façade application/transverse ;
- de rendre possible, plus tard, une extraction physique d'un domaine sans devoir réécrire tous les appels
  métier.

## Cible d'architecture

```text
                         Application / Use Cases
                                  │
                  ┌───────────────┴───────────────┐
                  ↓                               ↓
            Ports de lecture                 Commands
                  │                               │
                  └──────────────┬────────────────┘
                                 ↓
                     Persistence adapters
                                 ↓
                    Repositories / JPA entities

  Domaine A    Domaine B    Domaine C    Banque    ...
     │            │            │           │
     └────────────┴────────────┴───────────┘
                      même PostgreSQL
                  mais ownership explicite
```

Le principe fondamental est **séparation logique avant séparation physique**.

## Objectifs

### O1 — Supprimer la dépendance métier → `PersistenceManager`

Aucun moteur de calcul ne doit connaître `PersistenceManager`. Un service d'application peut encore l'utiliser
pendant la transition lorsqu'il compose plusieurs ports ou exécute une opération globale.

### O2 — Donner un propriétaire à chaque donnée persistée

Exemples de propriétaire :

| Données | Propriétaire métier cible |
|---|---|
| revenus / charges / lignes de trésorerie | Trésorerie |
| placements / immobilier / catégories d'actifs | Patrimoine |
| paramètres et projections retraite | Retraite |
| enfants fiscaux / tranches / overrides | Fiscalité |
| import bancaire / transactions / pointage | Banque |
| prêts | Crédit |
| objectifs | Objectifs |
| notifications / abonnement push | Notifications / infrastructure applicative |

`Settings` reste une façade REST/application : ses champs sont stockés chez leur propriétaire métier, il n'y a
pas vocation à créer un nouveau « domaine Settings » monolithique.

### O3 — Supprimer `BudgetDataModel`, composer l'export à partir de fragments de silo

*(Révisé le 4 octobre 2026 : l'ancienne version faisait survivre le type comme snapshot.)*

`BudgetDataModel` disparaît (SILO-230). Chaque silo expose `export`, `replace` et `reset` sur ses propres records (D6). Pour
l'export, l'import, la sauvegarde et la restauration, l'application compose et décompose le JSON global à partir de ces
fragments et exécute l'import dans une seule transaction. Le format JSON `/budget`, `/budget/import` et `/budget/reset` est conservé côté web.

Usage de transition : strictement borné par la liste fermée de SILO-002, chaque entrée nommant le patch qui la supprime.

### O4 — Retirer `BudgetDataEntity` du rôle de racine relationnelle universelle

Une entité métier doit pouvoir être persistée et chargée sans navigation ORM vers toutes les autres familles.
Les relations inter-domaines doivent progressivement être représentées par identifiants, projections ou ports,
pas par `@ManyToOne` / `@OneToMany` traversant les frontières.

### O5 — Préserver l'atomicité quand elle est métier

Une opération multi-domaines telle que la mise à jour de Settings peut rester transactionnelle puisqu'on est dans
une même application et une même base. Le découpage de code ne justifie pas, à lui seul, un découpage des
transactions.

## Deux paliers volontairement séparés

### Palier 1 — frontières de persistance

C'est le palier prioritaire avant Maven.

```text
Application Service
    ↓
Domain Reader / Command
    ↓
Persistence Adapter
    ↓
Persistence implementation actuelle
```

L'adapter peut encore déléguer à `PersistenceManager` ou reconstruire temporairement un `BudgetDataModel` en interne.
Le couplage résiduel est alors confiné dans l'adapter au lieu de contaminer tout le code.

### Palier 2 — séparation JPA

C'est le chantier de risque élevé.

Pour chaque domaine :

1. définir les entités qui lui appartiennent ;
2. créer les repositories correspondants ;
3. migrer le mapper/converter ;
4. supprimer ses relations ORM vers `BudgetDataEntity` lorsque leur rôle est devenu inutile ;
5. valider en PostgreSQL ;
6. seulement ensuite supprimer l'ancien chemin de persistance.

Il ne faut **jamais** convertir les ~27 entités en une seule opération monolithique : chaque domaine doit avoir un
patch court, une validation et un rollback clair.

## Ordre de migration recommandé

L'ordre n'est pas une hiérarchie de valeur métier. Il est choisi pour réduire les conflits et les risques.

### P0 — Stabilisation des frontières

- lecteurs par domaine ;
- commandes par domaine ;
- décision sur le rôle résiduel de `PersistenceManager` ;
- tests de round-trip et de redémarrage.

Cette phase peut démarrer dès que les contrats métier correspondants sont suffisamment stables.

### P1 — domaines à faible dépendance transverse

- Retraite ;
- Fiscalité ;
- Objectifs ;
- Banque.

Ces domaines permettent d'exercer la méthode sur des sous-graphes de données relativement identifiables.

### P2 — Patrimoine

Dépendances vers placements, catégories d'actifs, immobilier et historique de placement. Le chantier doit rester
strictement propriétaire de ces données et ne pas réintroduire la trésorerie via une relation ORM.

### P3 — Trésorerie

Elle possède beaucoup de données de base et reste une grande composante d'assemblage. Sa persistance doit être
séparée de ses projections calculées.

### P4 — Crédit / Prêts

À migrer lorsque les contrats de crédit sont déjà suffisamment autonomes.

### P5 — Suppression du hub

Une fois les domaines migrés, éliminer progressivement :

- la navigation globale de `BudgetDataEntity` ;
- les repositories qui ne servent plus qu'à reconstruire le snapshot global ;
- les conversions globales de `EntityModelConverter` ;
- la responsabilité mutationnelle de `PersistenceManager`.

## Ce qui reste global

Certaines opérations ne sont pas raisonnablement attachables à un domaine unique :

### Import/export/backup/reset global

`/budget`, `/budget/import` et `/budget/reset` manipulent l'ensemble du budget. Ils peuvent conserver une façade
transverse qui coordonne plusieurs ports de persistance.

### Vue `/settings`

La ressource REST reste composite. Son application service distribue les champs vers les propriétaires : Retraite,
Fiscalité, Trésorerie, Objectifs, Simulation et EconomicAssumptions, selon la décision décrite dans
`12-settings.md`.

### Snapshot `BudgetDataModel`

*(Révisé le 4 octobre 2026.)* Il ne survit pas : les formats d'échange globaux sont composés par l'application à partir des
fragments de silo (voir O3).

## Contraintes

### C1 — Une seule base PostgreSQL, des tables par silo

*(Révisé le 4 octobre 2026.)* Le but est de séparer les responsabilités, pas de distribuer le système. Une base, un seul
`PlatformTransactionManager`, une même `EntityManagerFactory` ; chaque silo possède ses entités, repositories et mappers, avec des
tables préfixées par silo et aucune clé étrangère entre silos (D4). C'est ce qui rend possible une transaction couvrant plusieurs
silos, ouverte par l'application via un port `TransactionRunner`. Une base physique par silo serait une décision d'infrastructure ultérieure.

### C2 — Pas de référence JPA inter-domaines

Une relation ORM pratique qui franchit une frontière est considérée comme une dépendance de conception, même si la
base reste unique.

### C3 — Tests PostgreSQL obligatoires pour les migrations ORM significatives

H2 en `create-drop` reste précieux pour la boucle rapide, mais ne valide pas suffisamment les comportements de
transactions/dialecte/LOB observés en production.

### C4 — Le cache mémoire n'est pas la source de vérité

`BudgetCacheStore` doit conserver le contrat déjà recherché : la persistance réussit avant que le nouvel état ne
soit publié en mémoire ; en cas d'échec DB, l'ancien état reste observable.

### C5 — Une modification de schéma doit rester réversible

Chaque patch JPA doit pouvoir être isolé et identifié. Les suppressions d'anciennes relations viennent après une
preuve de non-régression, pas avant. Le système de migration de schéma retenu est Liquibase (D5), à introduire par le patch qui en a besoin.

## Risques et limites

### R1 — Les annotations JPA ne montrent pas toute la sémantique

Cascade, `orphanRemoval`, ordre de flush et contraintes SQL peuvent provoquer des erreurs uniquement à l'exécution.
Une lecture du code n'est donc pas une validation suffisante.

### R2 — `EntityModelConverter` est encore global

La séparation doit progressivement créer des convertisseurs par domaine. Le laisser global trop longtemps recrée
une dépendance cachée entre modules Maven.

### R3 — Les imports/exports sont composés par l'application

*(Révisé le 4 octobre 2026.)* La forme globale n'existe plus que comme document JSON assemblé par l'application à partir des
fragments de silo ; aucun type global n'est utilisé par les calculateurs, les repositories ni les services de silo.

### R4 — Pas d'extraction microservice pendant cette phase

Une bonne séparation doit rendre une extraction future possible, pas l'imposer maintenant. Tant que toutes les
opérations sont dans la même application et la même base, une transaction locale multi-domaines reste souvent plus
simple et plus fiable.

## Critères de sortie avant Maven

- chaque domaine migré dispose d'un owner explicite ;
- les services applicatifs lisent via des ports, et non directement via `PersistenceManager` ;
- les écritures métier passent par des commandes explicites ;
- `BudgetDataModel` n'est plus nécessaire pour une opération locale à un domaine ;
- `BudgetDataEntity` n'est plus le contrat technique d'un domaine ;
- les migrations JPA déjà réalisées ont des tests de round-trip et de redémarrage ;
- le comportement PostgreSQL est vérifié ;
- `PersistenceManager` est réduit à une façade transverse ou de transition clairement bornée ;
- les scénarios Playwright critiques restent verts avec fallback désactivé ;
- les règles ArchUnit interdisent le retour du couplage.

## Relations avec les autres documents

- Principes et graphe de dépendances : [`00-principes.md`](00-principes.md)
- Séquencement métier : [`01-sequencement.md`](01-sequencement.md)
- Settings : [`12-settings.md`](12-settings.md)
- Checklist avant Maven : [`14-checklist-maven.md`](14-checklist-maven.md)
- Backlog précédent de découplage : [`15-backlog-patchs.md`](15-backlog-patchs.md)
- Backlog spécifique persistance : [`18-backlog-persistance-patchs.md`](18-backlog-persistance-patchs.md)
- Stratégie de validation : [`16-tests.md`](16-tests.md)
