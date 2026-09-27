# 14 — Checklist « prêt pour les modules Maven »

Statut : 🟡 à valider

Le découpage Maven ne doit démarrer que lorsque les conditions ci-dessous sont réunies. Cases à
cocher au fur et à mesure, domaine par domaine (ne pas attendre d'avoir tout coché une seule fois
à la fin).

## Modèles

- [ ] `BudgetDataModel` n'est plus un argument des calculateurs métier (voir l'indicateur de fin
      de chantier dans [11-domaine-overview.md](11-domaine-overview.md)).
- [ ] Les `SettingsModel` composites ont été séparés conceptuellement (voir
      [12-settings.md](12-settings.md)).
- [ ] Les paramètres retraite dupliqués dans Settings (`pass2026`, `passGrowthRate`) ont été
      tranchés.
- [ ] Chaque modèle persistant possède un propriétaire explicite.

## Entrées

- [ ] Chaque use case dispose d'un Input dédié.
- [ ] Aucun Input n'est un simple alias de `BudgetDataModel`.
- [ ] Les projections inter-domaines sont définies (graphe de
      [00-principes.md](00-principes.md)).
- [ ] Les assemblers responsables des projections sont identifiés.

## Sorties

- [ ] Les résultats métier ne transportent plus `BudgetDataModel` (`OverviewResultModel.data`,
      `AnalyseResultModel.data`).
- [ ] Les résultats métier ne transportent plus de modèles sources inutiles.
- [ ] Les ViewModels API sont clairement séparés des résultats de calcul.

## Calculs

- [ ] Un seul moteur retraite existe (plus de triplication Overview/Trésorerie/Retraite).
- [ ] La fiscalité consomme un revenu de retraite déjà projeté.
- [ ] Patrimoine ne possède plus de mini-moteur de trésorerie caché.
- [ ] Trésorerie est séparée conceptuellement en plusieurs use cases (projection, moyennes
      réelles, suggestions).
- [ ] Notifications ne dépendent plus d'un contexte budget global.

## Persistance (palier 1 uniquement — voir [13-persistance.md](13-persistance.md))

- [ ] Les moteurs métier ne connaissent plus `PersistenceManager`.
- [ ] Les ports de lecture/écriture par domaine sont identifiés (implémentation encore adossée à
      `PersistenceManager` acceptée à ce stade).
- [ ] `PersistenceManager` est clairement identifié comme façade de transition.
- [ ] Les mutations génériques (`listKey`/`field`/`value`) sont progressivement remplacées par des
      commandes métier.

*Le palier 2 (entités JPA séparées par domaine) n'est délibérément pas une condition de cette
checklist — voir [13-persistance.md](13-persistance.md).*

## API

- [ ] Les tags OpenAPI correspondent aux capabilities définies.
- [ ] Les contrats composites hérités ont été identifiés.
- [ ] Le split OpenAPI est planifié.
- [ ] Les futurs modules peuvent posséder leurs contrats sans dépendre d'un contrat monolithique.

## Tests et garde-fous

- [ ] Les calculs critiques disposent de tests unitaires indépendants de la persistance.
- [ ] Les tests de mapping sont séparés des tests métier.
- [ ] Les tests d'intégration couvrent les assemblers/orchestration.
- [ ] Les scénarios de référence (fiscal, retraite, trésorerie, patrimoine, prêts) de l'étape 0
      sont conservés et toujours verts.
- [ ] Les règles de notification ont des tests unitaires indépendants.
- [ ] Une règle ArchUnit existe et passe pour chaque domaine migré (pas seulement en fin de
      chantier — voir [01-sequencement.md](01-sequencement.md)).

## Séparation physique des bases (hors périmètre de cette checklist)

Une éventuelle base physique par domaine reste une décision d'infrastructure ultérieure et
optionnelle, à évaluer seulement après stabilisation des modules Maven, et seulement si un
bénéfice concret apparaît (déploiement indépendant, montée en charge indépendante, contrainte de
sécurité/disponibilité). Elle n'est **pas** un objectif de ce chantier.
