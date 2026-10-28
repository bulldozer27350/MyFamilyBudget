package com.moe.myfamilybudget.application.usecase.wealth;

/**
 * Une catégorie de l'import bancaire, rappelée dans la réponse Patrimoine (lecture inter-silo,
 * domaine Banque/Pointage ; seules les catégories sont utilisées par cette réponse).
 */
public record BankImportCategoryModel(
    String id,
    String label,
    String kind,
    String compressible
) {}
