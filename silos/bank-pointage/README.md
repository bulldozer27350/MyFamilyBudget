# MyFamilyBudget — Silo Banque/Pointage (`domain-bank-pointage`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Banque/Pointage** de MyFamilyBudget.

## Structure des modules

- **`bank-pointage-api`** : contrats, ports, interfaces de service (import bancaire, pointage). Purement JDK (sans dépendance externe Spring ni JPA).
- **`bank-pointage-core`** : moteurs de calcul purs, intégration Enable Banking sans Spring, et persistance JPA du document d'import bancaire.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
