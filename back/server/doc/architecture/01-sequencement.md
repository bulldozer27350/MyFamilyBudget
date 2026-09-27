# 01 — Séquencement retenu

Statut : 🟡 à valider

## Pourquoi ce fichier existe

Le document source proposait deux ordres différents pour la suite du chantier (une séquence
détaillée domaine par domaine, et une séquence lettrée plus tardive résumant l'ensemble), qui ne
plaçaient pas la persistance et les garde-fous au même endroit. Ce fichier tranche et devient la
seule référence d'ordonnancement.

## Étape 0 — sécurisation (préalable à tout)

- build vert, tests backend et e2e verts ;
- snapshot de référence des principales réponses API (fiscal, retraite, trésorerie, patrimoine,
  prêts) pour détecter toute régression de comportement pendant la migration.

## Étape 1 — normaliser les frontières + garde-fou minimal

- créer les concepts et records d'entrée/sortie décrits dans ce dossier, sans déplacer les
  packages ;
- **dès cette étape**, ajouter une première règle ArchUnit minimale : le package `calculation`
  ne dépend pas de `BudgetDataModel`. C'est la correction principale par rapport au document
  source, qui ne plaçait ce type de garde-fou qu'en toute dernière étape (donc après 8 à 9
  migrations de domaine faites sans aucune vérification automatique). La règle sera étendue
  domaine par domaine, immédiatement après chaque migration ci-dessous — jamais en un seul bloc
  à la fin.

## Étape 2 — Retraite (priorité — voir [03-domaine-retraite.md](03-domaine-retraite.md))

Choisie en premier non pas parce que c'est le domaine le plus simple, mais parce que le calcul de
projection retraite est aujourd'hui **dupliqué à l'identique dans trois classes**
(`OverviewCalculationService`, `TresorerieCalculationService`, `RetraiteServiceImpl` ont chacune
leur propre `computeRetirementProjection`) — vérifié dans le code. Centraliser ce moteur corrige
un vrai risque de divergence de calcul, pas seulement un problème de couplage.

## Étape 3 — Fiscalité (voir [04-domaine-fiscalite.md](04-domaine-fiscalite.md))

Consomme la projection de retraite (issue de l'étape 2) au lieu de recalculer une pension en
interne.

## Étape 4 — Patrimoine (voir [05-domaine-patrimoine.md](05-domaine-patrimoine.md))

Isole la projection de placement et le mécanisme pause/sweep, aujourd'hui mêlés au contrôleur
REST.

## Étape 5 — Trésorerie (voir [06-domaine-tresorerie.md](06-domaine-tresorerie.md))

Assemble les projections de Fiscalité, Retraite et Patrimoine dans un `TreasuryProjectionInput`.
L'ordre Patrimoine (4) avant Trésorerie (5) est intentionnel : Trésorerie consomme une projection
de cash-flow produite par Patrimoine.

## Étape 6 — Banque / Pointage (voir [07-domaine-banque-pointage.md](07-domaine-banque-pointage.md))

Peut avancer en parallèle des étapes précédentes : `BankImportCalculator` est déjà un calculateur
pur, sans Spring ni DTO. Le travail porte surtout sur `PointageModel`, qui agrège encore
plusieurs domaines.

## Étape 7 — Analyse (voir [08-domaine-analyse.md](08-domaine-analyse.md))

Remplace l'agrégat `BudgetDataModel` + `BankImportModel` par des projections ciblées
(`BudgetLineProjection`).

## Étape 8 — Objectifs / Notifications (voir [09-domaine-objectifs-notifications.md](09-domaine-objectifs-notifications.md))

## Étape 9 — Prêts / Suggestions (voir [10-domaine-prets-suggestions.md](10-domaine-prets-suggestions.md))

Le candidat le plus mûr du dépôt (calcul déjà pur) — bon test de non-régression de la méthode
avant de l'appliquer aux domaines plus gros, mais planifié après Retraite dans cet ordre car il
ne corrige aucun bug latent connu, contrairement à Retraite.

## Étape 10 — Overview (voir [11-domaine-overview.md](11-domaine-overview.md))

Devient un pur agrégateur de projections déjà calculées par les étapes précédentes.

## Étape 11 — Mutations

Remplacer progressivement les mutations génériques `listKey` / `field` / `value` par des
commandes orientées domaine (`PatrimoineCommandService`, `RetirementCommandService`, etc.).

## Étape 12 — Persistance (voir [13-persistance.md](13-persistance.md))

Découpée en deux paliers de risque distincts — voir le fichier dédié. Le palier 1 (ports de
lecture/écriture par domaine, adossés en interne à `PersistenceManager`) peut commencer dès que
2-3 domaines métier sont stabilisés, sans attendre la fin des étapes 2-10. Le palier 2 (entités
JPA séparées par domaine, sans relation inter-domaines) est délibérément traité comme un chantier
ultérieur et séquencé domaine par domaine, pas comme un préalable bloquant aux modules Maven.

## Étape 13 — OpenAPI

Scinder `openapi.yaml` selon les capabilities définies par les domaines, une fois leurs contrats
stabilisés.

## Étape 14 — Point d'arrêt

Les frontières sont prêtes. Passage à la checklist [14-checklist-maven.md](14-checklist-maven.md)
avant de créer les modules Maven.
