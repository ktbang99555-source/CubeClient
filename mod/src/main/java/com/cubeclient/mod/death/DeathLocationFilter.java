package com.cubeclient.mod.death;

import java.util.List;
import java.util.stream.Collectors;

/** 저장된 죽은 위치 중 "지금 있는 월드/차원"과 일치하는 것만 걸러낸다. */
public final class DeathLocationFilter {
    private DeathLocationFilter() {}

    public static List<DeathLocation> forCurrentWorld(
            List<DeathLocation> all, String currentWorldId, String currentDimensionId) {
        return all.stream()
            .filter(loc -> loc.worldId().equals(currentWorldId) && loc.dimensionId().equals(currentDimensionId))
            .collect(Collectors.toList());
    }
}
