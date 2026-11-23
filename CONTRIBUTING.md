# Convention de commit — Conventional Commits

Ce dépôt suit la convention [Conventional Commits](https://www.conventionalcommits.org/fr/). Elle
alimente l'automatisation du cycle de release décrite dans
`back/server/doc/architecture/27-backlog-automatisation-release.md` : génération du changelog
(REL-040) et choix automatique du type de montée de version (REL-050). Les dépôts satellites
(`myfamilybudget-domain-*`) suivent la même convention (voir leur propre `CONTRIBUTING.md`).

## Format

```
<type>(<portée optionnelle>): <description>

<corps optionnel>

<pied de page optionnel>
```

## Types autorisés

| Type | Usage |
|---|---|
| `feat` | Nouvelle fonctionnalité |
| `fix` | Correction de bug |
| `docs` | Documentation uniquement |
| `style` | Mise en forme (espaces, point-virgules…), sans effet sur le comportement |
| `refactor` | Changement de code sans ajout de fonctionnalité ni correction de bug |
| `perf` | Amélioration de performance |
| `test` | Ajout ou correction de tests |
| `build` | Build, dépendances, outillage |
| `ci` | Intégration continue |
| `chore` | Tâche de maintenance ne rentrant dans aucune autre catégorie |
| `revert` | Annulation d'un commit précédent |

## Breaking change

Un changement d'API non rétrocompatible se signale de deux façons (combinables) :
- `!` juste après le type ou la portée : `feat!: ...` ou `feat(api)!: ...`
- un pied de page `BREAKING CHANGE: <description>` dans le corps du commit

## Exemples

- `fix(credit): corrige le calcul du CRD pour les tranches commençant dans le mois courant`
- `feat(api): ajoute le champ cashAlertThreshold`
- `feat(api)!: retire le champ cashFloor (remplacé par cashAlertThreshold)`
- `chore: met à jour flatten-maven-plugin vers 1.6.0`
- `docs: documente la convention de commit`

## Validation

Un job de CI (`lint-commits`) vérifie automatiquement, sur chaque push d'une branche de travail,
que les messages de commit depuis le point de divergence avec `main` respectent ce format. Les
commits de fusion (`Merge ...`) sont exclus de la vérification.
