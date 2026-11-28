# 28 — Backlog : provisionnement des lignes budgétaires périodiques

Statut : 🟡 v1 (28 novembre 2026)

## 1. Pourquoi ce document

Le budget mensuel est aujourd'hui construit en divisant chaque dépense annuelle par 12 (montant
« mensualisé »). Pour une ligne qui tombe réellement tous les mois, c'est exact. Pour une ligne
qui ne tombe que certains mois (rattrapage d'impôts, assurance habitation, prélèvement
semestriel...), la mensualisation reste la bonne façon de provisionner la somme, mais le pointage
mensuel actuel ne sait rien en faire : rien n'est pointé les mois sans échéance réelle, et rien
n'indique que la somme a bien été mise de côté en vue de l'échéance.

Ce backlog découpe la mise en place d'un provisionnement explicite de ces lignes en patchs
indépendants, répartis sur les dépôts satellites concernés, pour pouvoir être traités par plusieurs
agents en parallèle. Convention d'identifiants : `PROV-nn`.

## 2. Principes retenus (à ne pas rouvrir sans en discuter)

- Une ligne budgétaire (`ChargeModel` côté Trésorerie) gagne un champ `dueMonths` : liste de mois
  (1 à 12) où la dépense réelle survient. **Vide ou absent = comportement actuel, inchangé**
  (ligne mensuelle, échéance tous les mois).
- Le champ `monthly` existant ne change pas de sens : c'est toujours le montant mensuel provisionné
  (montant annuel / 12), celui qui sert de base au calcul du budget. Il n'est pas recalculé quand on
  ajoute ou retire un mois d'échéance.
- Le **montant par échéance** (celui attendu en banque le ou les mois où la dépense tombe
  réellement) est une valeur dérivée, jamais stockée : `montant_par_echeance = monthly * 12 /
  nombre_de_mois_d_echeance`. Ajouter un deuxième mois d'échéance divise ce montant par deux,
  l'ajouter un troisième par trois, etc. (le total annuel provisionné ne change pas).
- Les mois **hors échéance** d'une ligne périodique ne sont plus laissés vides dans le pointage :
  l'utilisateur coche que la somme mensuelle a bien été mise de côté (compte séparé, placement, peu
  importe — rien n'est tracé au-delà de la case cochée dans ce premier lot).
- Les mois **d'échéance** gardent le pointage bancaire habituel, mais le montant attendu devient le
  montant par échéance (calcul ci-dessus) au lieu du 1/12e.
- `BudgetLineProjection` (dépôt `myfamilybudget-domain-bank-pointage`) et `AnalysisBudgetLine`
  (dépôt `myfamilybudget-domain-analysis`) sont deux copies indépendantes du même type frontière
  (pas de module partagé, conformément à `27-strategie-multi-depots-bom-semver.md`) : toute
  évolution de forme doit être livrée dans les deux dépôts, traduite depuis `ChargeModel` par
  l'intégrateur (`MyFamilyBudget`), qui reste le seul à connaître les deux bouts.
- Le format de stockage exact de `dueMonths` (colonne texte façon `start_date`/`end_date`, ou table
  de jointure) est laissé au choix de l'agent qui traite PROV-01, à documenter dans son patch.

## 3. Backlog

| ID | Patch | Dépôt | Dépend de | Bloque | Statut |
|---|---|---|---|---|---|
| PROV-00 | Ce document (principes, format des échanges, vocabulaire des statuts de pointage) | MyFamilyBudget | — | PROV-01 à PROV-09 | Fait (ce patch) |
| PROV-01 | Trésorerie : `ChargeModel`/`CashflowChargeEntity` + `dueMonths` ; méthode dérivée du montant par échéance ; script Liquibase | myfamilybudget-domain-treasury | PROV-00 | PROV-02, PROV-03 | À faire |
| PROV-02 | Intégrateur : contrat REST des charges (`openapi/`, mapper Trésorerie) étendu avec `dueMonths` ; validation (mois 1-12, sans doublon) ; vue budget : édition des mois d'échéance avec recalcul en direct du montant par échéance | MyFamilyBudget | PROV-01 | — | À faire |
| PROV-03 | Transport de `dueMonths` jusqu'aux silos consommateurs : `BudgetLineProjection` (Pointage) et `AnalysisBudgetLine`/`MonthlyBudgetLines` (Analyse) étendus ; `PointageInputFactory`/`AnalyseInputFactory` (intégrateur) adaptées pour le transporter | myfamilybudget-domain-bank-pointage, myfamilybudget-domain-analysis, MyFamilyBudget | PROV-01 | PROV-04, PROV-07 | À faire |
| PROV-04 | Bank-Pointage : nouveaux statuts de ligne pour les mois hors échéance d'une ligne périodique (`to_provision` / `provisioned`) ; `DefaultPointageCalculationService` : détection du mois d'échéance via `dueMonths`, calcul de l'attendu (montant par échéance ou 0) ; persistance de la case cochée (nouvelle table ou colonne, migration Liquibase propre au silo) ; endpoint de bascule coché/non coché | myfamilybudget-domain-bank-pointage | PROV-03 | PROV-05 | À faire |
| PROV-05 | Intégrateur : exposition REST de la bascule de provision (DTO OpenAPI, câblage `BankPointageUseCase`/contrôleur) | MyFamilyBudget | PROV-04 | PROV-06 | À faire |
| PROV-06 | Front, vue Pointage : case à cocher « mis de côté » sur les mois hors échéance d'une ligne périodique, visuel distinct (couleur/pastille) selon coché ou non ; mois d'échéance : pointage bancaire habituel avec le montant par échéance comme attendu | MyFamilyBudget | PROV-05 | — | À faire |
| PROV-07 | Analyse : nouveau calcul de solde de provision par ligne périodique (mois par mois : +1/12e si la case est cochée ce mois-là, −montant par échéance le mois de sortie, cumul) ; nécessite l'état coché/non coché de PROV-04 en entrée du contrat `AnalyseInput` | myfamilybudget-domain-analysis | PROV-03, PROV-04 | PROV-08 | À faire |
| PROV-08 | Intégrateur + front : nouveau tableau dans la vue Analyse (mois par mois, somme à provisionner, cumul, mois de sortie réelle, visuel coché/non coché par ligne) ; contrat REST de la vue Analyse étendu en conséquence | MyFamilyBudget | PROV-07 | — | À faire |
| PROV-09 | Notifications : alerte avant un mois d'échéance d'une ligne périodique (M-1 ou M-2, seuil à discuter) ; pré-remplissage du montant par échéance dans les opérations à venir (porté par Bank-Pointage, `PendingOperationsServiceImpl`) | myfamilybudget-domain-notifications, myfamilybudget-domain-bank-pointage, MyFamilyBudget | PROV-04 | — | À faire (optionnel, peut suivre après PROV-08) |

## 4. Ordre conseillé

Chemin critique : PROV-01 → PROV-02 et PROV-03 (parallélisables) → PROV-04 → PROV-05 → PROV-06,
et séparément PROV-04 → PROV-07 → PROV-08. PROV-09 peut être traité en dernier, indépendamment du
reste de l'affichage.

## 5. Points ouverts

- Faut-il aussi couvrir les revenus exceptionnels (ligne `IncomeModel`, ex. prime versée deux fois
  par an) avec le même mécanisme, ou se limiter aux charges pour ce premier lot ? Ce backlog
  suppose le second choix (charges uniquement) ; `IncomeModel` n'est pas touché.
- Nom définitif des deux nouveaux statuts de pointage (`to_provision`/`provisioned` proposés en
  PROV-04) à confirmer avant de l'écrire dans le contrat REST, pour éviter un renommage a
  posteriori.
