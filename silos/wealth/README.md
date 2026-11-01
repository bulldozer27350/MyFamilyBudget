# MyFamilyBudget — Silo Patrimoine (`domain-wealth`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Patrimoine** de MyFamilyBudget.

## Structure des modules

- **`wealth-api`** : contrats, ports, interfaces de service (projection patrimoniale, évolution des placements). Purement JDK (sans dépendance externe Spring ni JPA).
- **`wealth-core`** : moteurs de calcul purs, et persistance JPA des placements, de l'immobilier et des catégories d'actifs.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
