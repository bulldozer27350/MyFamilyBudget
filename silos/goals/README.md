# MyFamilyBudget — Silo Objectifs (`domain-goals`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Objectifs** de MyFamilyBudget.

## Structure des modules

- **`goals-api`** : modèles, ports, paramètres. Purement JDK (sans dépendance externe Spring ni JPA).
- **`goals-core`** : persistance JPA des tables goal, goal_allocation et objectifs_settings.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
