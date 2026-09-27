# 02 — Budget de base (sous-domaine partagé)

Statut : 🟡 à valider

## Pourquoi un domaine à part

`IncomeModel`, `ChargeModel`, `OneOffExpenseModel` et les revenus variables sont consommés par
plusieurs domaines indépendants : Fiscalité, Retraite (projection de salaire), Analyse (réel vs
prévisionnel), Pointage, et plusieurs projections globales. Les rattacher naïvement au domaine
Trésorerie créerait une dépendance artificielle de tous ces consommateurs vers Trésorerie.

## Contenu du domaine

```text
Budget de base
├── Income
├── Charge
├── OneOffExpense
├── VariableIncomeRule
├── VariableIncomeOverride
└── Transfer
```

Attention : ce domaine ne doit **pas** devenir un nouveau « mini `BudgetDataModel` ». Il reste
limité aux éléments réellement transverses ; les mécanismes de simulation (projection annuelle,
horizon, croissance) restent chez leurs consommateurs (Trésorerie, Fiscalité, Retraite...), qui
reçoivent des lignes déjà normalisées plutôt que de réimplémenter leur propre lecture du modèle
brut.

## `TransferModel` : cas particulier

C'est l'un des rares modèles pour lesquels plusieurs domaines ont une vraie raison métier de le
consulter (Trésorerie, Patrimoine, indirectement Fiscalité, Overview). La bonne solution n'est pas
de le rendre accessible partout tel quel, mais d'en faire un petit objet transverse du domaine
Budget de base :

```java
public record Transfer(
    String id,
    String placementRef,
    LocalDate date,
    BigDecimal amount,
    String notes
) {}
```

Les consommateurs construisent ensuite leur propre projection locale si leurs besoins divergent
(`TreasuryTransfer`, `PatrimoineTransfer`).
