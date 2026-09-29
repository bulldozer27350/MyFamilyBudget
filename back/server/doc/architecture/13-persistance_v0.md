# 13 — Persistance : deux paliers de risque distincts

Statut : 🟡 à valider

## Constat sur l'existant

Contrairement à une hypothèse de travail antérieure, la persistance actuelle n'est pas un simple
blob JSON unique. Seul `bankImport` est stocké en JSON texte (`BankImportEntity.jsonData`, un
choix technique documenté et volontaire pour éviter le mécanisme PostgreSQL « Large Object »).
Tout le reste (27 entités : `SettingsEntity`, `IncomeEntity`, `PlacementEntity`,
`RetirementEntity`, etc.) est un modèle JPA déjà normalisé, mais avec `BudgetDataEntity` comme
hub central relié par `@OneToMany`/`@OneToOne` à quasiment tout. C'est un vrai couplage physique à
défaire, pas une reformulation théorique — mais c'est aussi la partie la plus risquée et la moins
vérifiable du chantier (aucune exécution réelle possible dans l'environnement de génération de
patch, seule une relecture manuelle ; une erreur de cascade/`orphanRemoval` ne casse pas la
compilation, elle se manifeste à l'exécution).

Le document source classait la quasi-totalité des points de persistance comme « obligatoire »
avant les modules Maven, sans distinguer leur coût ni leur urgence réelle par rapport à l'objectif
initial (tests de composants + travail parallèle sans conflit). Ce fichier introduit cette
distinction.

## Palier 1 — obligatoire, faible risque : ports par domaine

Objectif : qu'aucun moteur métier ne dépende de `PersistenceManager`, sans toucher au schéma ni
aux entités JPA.

```text
Controller
   ↓
Application Service
   ↓
Read / Write ports (par domaine)
   ↓
Persistence adapters (peuvent, pour l'instant, continuer à s'appuyer sur PersistenceManager)
```

Concrètement, remplacer :

```java
persistenceManager.getBudgetData()
```

par une composition explicite de ports par domaine :

```text
budgetReader.getBudgetLines()
patrimoineReader.getPlacements()
retirementReader.getRetirement()
taxReader.getTaxRules()
bankReader.getBankState()
loanReader.getLoans()
goalReader.getGoals()
settingsReader.getXxxSettings()
```

**Point important du document source à conserver tel quel** : il n'est pas obligatoire que chacune
de ces interfaces existe déjà sous sa forme finale — leur implémentation peut, à ce stade,
continuer à déléguer à `PersistenceManager`/`BudgetDataModel` en interne. Ce qui compte est que
les *responsabilités* soient déjà séparables et que le moteur métier ne voie plus que le port, pas
l'implémentation. C'est ce palier, à lui seul, qui suffit à satisfaire l'objectif de testabilité
et de travail parallèle — il peut donc démarrer dès que 2 ou 3 domaines métier sont stabilisés
(voir [01-sequencement.md](01-sequencement.md)), sans attendre la fin de toutes les migrations
métier.

De même pour l'écriture : remplacer les mutations génériques de `PersistenceManager`
(`updateTresorerieRow`, `savePatrimoineRow`, `updateRetirement`, ...) par des commandes groupées
par propriétaire (`PatrimoineCommandService`, `RetirementCommandService`, `TaxCommandService`,
...). Raison de le faire avant les modules Maven : si les modules sont créés avant cette
clarification, `PersistenceManager` redeviendra rapidement le nouveau point de dépendance commun
et annulera une grande partie du bénéfice des modules.

## Palier 2 — optionnel à ce stade, risque plus élevé : entités JPA séparées par domaine

Objectif final (pas un préalable bloquant) : chaque domaine possède ses propres entités et
repositories JPA, sans relation `@ManyToOne`/`@OneToMany` inter-domaines (aujourd'hui, toutes les
entités pointent vers `BudgetDataEntity`).

- à traiter domaine par domaine, jamais en un seul chantier ;
- chaque domaine migré doit être testé en conditions réelles (base Postgres de production, pas
  seulement relecture manuelle) avant de passer au domaine suivant ;
- ne doit être entamé qu'une fois plusieurs domaines métier (palier ci-dessus + Inputs/Outputs)
  sont stables depuis un certain temps ;
- la séparation physique en plusieurs bases de données reste, elle, hors sujet à ce stade
  (décision d'infrastructure ultérieure, non nécessaire pour ce chantier — voir
  [14-checklist-maven.md](14-checklist-maven.md)).

## Ce qui ne doit pas transiter par la persistance

Comme rappelé dans [00-principes.md](00-principes.md), les moteurs métier ne doivent connaître ni
`BudgetDataModel`, ni `PersistenceManager`, ni une entité JPA, ni un DTO OpenAPI, ni une structure
de requête HTTP générique. C'est la seule règle vraiment non négociable de ce fichier ; le rythme
auquel les entités JPA elles-mêmes sont réorganisées est, lui, négociable et doit rester
proportionné au risque.
