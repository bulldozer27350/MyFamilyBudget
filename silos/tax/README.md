# MyFamilyBudget — Silo Fiscalité (`domain-tax`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Fiscalité** de MyFamilyBudget.

## Structure des modules

- **`tax-api`** : contrats, ports, interface de service (calcul fiscal, projections). Purement JDK (sans dépendance externe Spring ni JPA).
- **`tax-core`** : moteur de calcul et persistance JPA des tables fiscal_*.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
