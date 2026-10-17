package dev.asaul.litematicainventories;

import net.minecraft.core.BlockPos;

public record CacheKey(String server, String dimension, BlockPos pos) {
    public CacheKey {
        pos = pos.immutable();
    }
}
