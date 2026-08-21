package com.moe.myfamilybudget.server.internal.model;

public record AssetCategoryModel(
        String id,
        String icon,
        String name,
        String bucket
) {
    public AssetCategoryModel {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        if (name == null) {
            name = "";
        }
        if (icon == null) {
            icon = "📁";
        }
        if (bucket == null) {
            bucket = "cash";
        }
    }
}
