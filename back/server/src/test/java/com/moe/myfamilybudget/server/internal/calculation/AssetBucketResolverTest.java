package com.moe.myfamilybudget.server.internal.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

class AssetBucketResolverTest {

    private static final List<AssetCategoryModel> CATEGORIES = List.of(
            new AssetCategoryModel("c1", "💶", "Livrets", "cash"),
            new AssetCategoryModel("c2", "📈", "PEA", "actions"));

    private static PlacementModel placement(String category) {
        return new PlacementModel("p", "Placement", category, BigDecimal.TEN, "2026-09-01", BigDecimal.ZERO, null, null,
                new BigDecimal("0.01"), new BigDecimal("0.02"), new BigDecimal("0.03"), false, "");
    }

    @Test
    @DisplayName("Le bucket est retrouvé par nom de catégorie")
    void byName() {
        assertEquals("cash", new AssetBucketResolver(CATEGORIES).bucketOf(placement("Livrets")));
        assertEquals("actions", new AssetBucketResolver(CATEGORIES).bucketOf(placement("PEA")));
    }

    @Test
    @DisplayName("Catégorie inconnue ou liste nulle : bucket null")
    void unknown() {
        assertNull(new AssetBucketResolver(CATEGORIES).bucketOf(placement("Inconnue")));
        assertNull(new AssetBucketResolver(null).bucketOf(placement("Livrets")));
    }
}
