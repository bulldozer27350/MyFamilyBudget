package com.moe.myfamilybudget.server.internal.model;

public record AssetCategoryModel(
        String id,
        String icon,
        String name,
        String bucket,
        String color
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

    public AssetCategoryModel(String id, String icon, String name, String bucket) {
        this(id, icon, name, bucket, null);
    }
}

