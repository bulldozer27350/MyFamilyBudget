package com.moe.myfamilybudget.server.internal.calculation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

/**
 * Retrouve la classe d'actif (« bucket » : cash, fondsEuros, actions, obligations, immobilier,
 * epargneSalariale) d'un placement à partir des catégories d'actifs de l'utilisateur : d'abord par
 * identifiant de catégorie, à défaut par nom (comme le fait calculations.js côté front).
 */
public final class AssetBucketResolver {

    private final Map<String, String> bucketById = new HashMap<>();
    private final Map<String, String> bucketByName = new HashMap<>();

    public AssetBucketResolver(List<AssetCategoryModel> categories) {
        for (AssetCategoryModel c : categories == null ? List.<AssetCategoryModel>of() : categories) {
            bucketById.put(c.id(), c.bucket());
            bucketByName.put(c.name(), c.bucket());
        }
    }

    /** @return le bucket du placement, ou null si sa catégorie est inconnue */
    public String bucketOf(PlacementModel placement) {
        String bucket = placement.categoryId() != null ? bucketById.get(placement.categoryId()) : null;
        if (bucket == null && placement.category() != null) {
            bucket = bucketByName.get(placement.category());
        }
        return bucket;
    }
}
