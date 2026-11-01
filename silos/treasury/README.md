# MyFamilyBudget — Silo Trésorerie (`domain-treasury`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Trésorerie** de MyFamilyBudget.

## Structure des modules

- **`treasury-api`** : contrats, ports, interfaces de service (calcul de trésorerie). Purement JDK (sans dépendance externe Spring ni JPA).
- **`treasury-core`** : moteur de calcul pur, et persistance JPA des revenus, charges, ponctuels, virements et variables.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
