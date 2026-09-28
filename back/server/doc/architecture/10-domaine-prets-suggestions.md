# 10 — Crédit (Prêts) et Suggestions de taux

Statut : 🟡 à valider

## Prêts — le domaine le plus mûr du dépôt

`LoanAdviceCalculationService` est déjà séparé de `BudgetDataModel` ; le couplage restant est
surtout dans l'orchestration REST qui extrait directement plusieurs sous-modèles du budget
global.

Champs réellement utilisés d'un prêt : `id`, `label`, `crd`, `rate`, `monthly`, `insurance`,
`startDate`, `endDate`, `stepDate`. `initialAmount` et `totalInstallments` ne sont pas consommés
par le calcul observé.

Pour l'alternative liquide, le moteur a seulement besoin de `label`, `rateCorr` et du bucket
d'actif — la résolution du bucket (aujourd'hui via `PlacementModel.categoryId`/`category` et
`AssetCategoryModel.id`/`name`/`bucket`) peut être faite par l'appelant.

```java
public record LoanAdviceInput(
    List<LoanInput> loans,
    List<LiquidPlacementAlternative> alternatives,
    LoanAdviceParameters parameters,
    BigDecimal marketRate,
    LocalDate today
) {}

public record LoanInput(
    String id, String label, BigDecimal crd, BigDecimal rate, BigDecimal monthly,
    BigDecimal insurance, String startDate, String endDate, String stepDate
) {}
```

**Attention à ne pas sur-refactorer.** Pour les prêts, l'`Input` est surtout une encapsulation de
frontière ; le gain principal est de sortir les modèles persistants du moteur et de faire de la
résolution des alternatives une responsabilité d'assemblage — pas de réinventer le calcul.

## Suggestions de taux

`PlacementRateSuggestionService` est déjà proche de la cible (placements, catégories d'actif,
données de marché, amplitude, date du jour). Dernier verrou : le modèle d'entrée.

```text
PlacementRateSuggestionInput
├── placements (id, label, category, taux actuels, bucket)
├── market
├── amplitude
└── today
```

**Décision (RF-802)** : `AssetBucketResolver` est un outil d'assemblage applicatif, pas un service
de domaine. Il vit dans `internal.factory`, utilisé uniquement par `LoanAdviceInputFactory` et
`PlacementRateSuggestionInputFactory` ; les moteurs reçoivent un bucket déjà résolu. Il ne doit pas
devenir une dépendance commune de tous les modules simplement parce qu'il est pratique.

## `AssetCategoryModel` : classification, pas modèle global

Consommé aujourd'hui par Patrimoine, Prêts, Suggestions, Settings, Overview/affichage. Deux rôles
à distinguer : classification configurable par l'utilisateur (`AssetCategory`) et résolution du
bucket utilisé par les calculs (`AssetClassificationSnapshot`, forme minimale — ex. le moteur des
prêts a besoin de `placementId`/`label`/`netYield`/`bucket`, pas de l'icône ni de la couleur
d'affichage).
