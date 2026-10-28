package com.moe.myfamilybudget.application.usecase.tax;

/** Un enfant à charge rappelé dans la réponse Fiscalité. */
public record TaxChildModel(
    String id,
    String name,
    Integer birthYear
) {}
