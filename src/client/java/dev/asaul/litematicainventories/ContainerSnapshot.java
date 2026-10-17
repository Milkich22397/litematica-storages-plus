package dev.asaul.litematicainventories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** Immutable raw registry-aware inventory data; never exposes its mutable tag. */
public record ContainerSnapshot(String blockId, String state, int slots, long observedAt, int dataVersion, CompoundTag blockEntityData) {
    public ContainerSnapshot { blockEntityData = blockEntityData.copy(); }
    @Override public CompoundTag blockEntityData() { return blockEntityData.copy(); }
    public Block block() { var id=Identifier.tryParse(blockId); return id==null?net.minecraft.world.level.block.Blocks.AIR:BuiltInRegistries.BLOCK.getValue(id); }
    public boolean stale() { long ttl = StorageSettings.INSTANCE.ttlHours; return ttl > 0 && System.currentTimeMillis() - observedAt > ttl * 3600000L; }
}
