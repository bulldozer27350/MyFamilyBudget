# MyFamilyBudget — Silo Retraite (`domain-retirement`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Retraite** de MyFamilyBudget.

## Structure des modules

- **`retirement-api`** : contrats, ports, interface de service (moteur de calcul, projections). Purement JDK (sans dépendance externe Spring ni JPA).
- **`retirement-core`** : moteur de calcul et persistance JPA des tables pension_*.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
