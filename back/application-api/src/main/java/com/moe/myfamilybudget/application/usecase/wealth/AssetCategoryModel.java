package com.moe.myfamilybudget.application.usecase.wealth;

/** Une catégorie d'actif, rappelée dans la réponse Patrimoine. */
public record AssetCategoryModel(
    String id,
    String icon,
    String name,
    String bucket,
    String color
) {}
