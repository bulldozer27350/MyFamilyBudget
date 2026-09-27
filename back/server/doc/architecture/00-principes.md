# 00 — Principes et contraintes

Statut : 🟡 à valider

Ce fichier fait office de constitution du chantier. Toute proposition de contrat (`XxxInput`,
`XxxResult`) ou de module doit s'y conformer.

## Ce qu'il ne faut surtout pas faire

- **Créer un nouveau modèle global.** Un `XxxInput` qui reprend tous les sous-modèles de
  `BudgetDataModel` sous un autre nom n'est pas une amélioration.
- **Mettre tous les nouveaux records dans un module « common ».** Un module fourre-tout partagé
  par commodité recrée le couplage qu'on cherche à supprimer, juste avec une compilation en plus.
- **Partager `SettingsModel` tel quel.** Voir [12-settings.md](12-settings.md) : chaque paramètre
  a un propriétaire métier unique.
- **Faire dépendre les agrégateurs (Overview, Analyse) des implémentations détaillées des
  domaines.** Ils consomment des projections/résultats, jamais les modèles source ni les
  services internes des autres domaines.
- **Faire dépendre un module métier de la persistance.** Aucun moteur de calcul ne doit connaître
  `BudgetDataModel`, `PersistenceManager`, une entité JPA, un DTO OpenAPI, ou une structure de
  requête HTTP (`Map<String,Object>`, `listKey`/`field`).

## Le test de conception à appliquer à chaque contrat ou feature

Avant d'ajouter un `Input`, un `Result`, ou une nouvelle fonctionnalité, poser ces quatre
questions :

1. Qui possède la donnée ?
2. Quel calcul possède la règle métier ?
3. Quelle projection minimale le consommateur doit-il recevoir ?
4. Quel contrat est stable entre les deux ?

**Si la réponse à la question 3 est « il lui faut `BudgetDataModel` », la frontière n'est pas
encore correctement définie.** C'est le signal d'alerte le plus fiable de tout ce chantier —
à repasser mentalement sur chaque nouveau patch, y compris après la fin de la migration.

## Les deux axes de fuite à traiter (entrées ET sorties)

Le réflexe naturel est de ne corriger que les paramètres d'entrée des moteurs de calcul. Le
second axe, tout aussi important, est la fuite par les résultats : plusieurs `XxxResultModel`
actuels retransportent encore un modèle source complet ou partiel vers les couches externes
(`OverviewResultModel.data` = `BudgetDataModel`, `AnalyseResultModel.data` = `BudgetDataModel`,
`RetraiteResultModel` qui embarque `IncomeModel`/`SettingsModel`, `TaxResultModel` qui embarque
`SettingsModel`...). Un `Result` métier ne doit exposer que :

- les résultats calculés ;
- les hypothèses nécessaires à leur interprétation ;
- des métadonnées de calcul.

jamais les modèles persistants dont il est issu. Si une page a besoin de données brutes en plus
du résultat de calcul, la couche application compose un ViewModel séparé — le `Result` du domaine
ne doit pas porter ce rôle.

## Graphe de dépendances cible

```text
                         ┌──────────────┐
                         │Budget de base│
                         └──────┬───────┘
                                │
              ┌─────────────────┼───────────────────┐
              ▼                 ▼                   ▼
        ┌───────────┐     ┌────────────┐      ┌───────────┐
        │ Retraite  │     │ Fiscalité  │      │ Trésorerie│
        └─────┬─────┘     └─────┬──────┘      └─────┬─────┘
              │                 │                   │
              └────────┬────────┘                   │
                       ▼                            │
                 projections                        │
                                                     │
       ┌───────────────┐                             │
       │  Patrimoine   │─────────────────────────────►
       └───────┬───────┘
               │
        ┌──────┴───────┐
        ▼              ▼
      Prêts          Objectifs
        │              │
        └──────┬───────┘
               ▼
          Notifications

Banque ──→ Pointage ──→ Analyse
   │                      │
   └──────→ Notifications

Marché ──→ Prêts
Marché ──→ SuggestionsTaux

Retraite + Fiscalité + Trésorerie + Patrimoine
                         ↓
                      Overview
```

Les flèches représentent des contrats/projections applicatives, jamais des accès directs aux
modèles persistants ou aux services internes d'un autre domaine.

Point d'attention : la flèche Patrimoine → Trésorerie doit être lue dans ce sens (Patrimoine
produit une projection de cash-flow consommée par Trésorerie — voir
[06-domaine-tresorerie.md](06-domaine-tresorerie.md)). Une relation retour existe potentiellement
(mécanisme de sweep de Trésorerie → décision de contribution consommée par Patrimoine) mais n'est
pas encore figée : voir la note dans
[05-domaine-patrimoine.md](05-domaine-patrimoine.md#point-ouvert).

## Anti-cycles Maven

Cycles à empêcher dès la conception des contrats (pas seulement au moment des modules) :

```text
Retraite ↔ Trésorerie
Fiscalité ↔ Trésorerie
Patrimoine ↔ Trésorerie
Objectifs ↔ Patrimoine
Notifications ↔ tous les domaines
Overview ↔ tous les domaines
```

Pour les quatre premiers, la solution est la projection unidirectionnelle. Pour Notifications et
Overview, ce sont des consommateurs finaux : ils restent en haut de la chaîne de dépendance,
jamais importés par les domaines qu'ils observent.
