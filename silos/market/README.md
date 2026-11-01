# MyFamilyBudget — Silo Marché (`domain-market`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Marché** de MyFamilyBudget.

## Structure des modules

- **`market-api`** : cotations, fraîcheur, ports, interface de service. Purement JDK (sans dépendance externe Spring ni JPA).
- **`market-core`** : clients des sources publiques et agrégation, et persistance JPA de l'instantané de marché.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
