# MyFamilyBudget — Silo Analyse (`domain-analysis`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Analyse** de MyFamilyBudget.

## Structure des modules

- **`analysis-api`** : contrats, modèles, interface de service. Purement JDK (sans dépendance externe Spring ni JPA).
- **`analysis-core`** : moteur de calcul pur, sans Spring ni JPA.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
