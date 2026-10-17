package dev.asaul.litematicainventories.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
@Mixin(value=MaterialListPlacement.class,remap=false)
public interface MaterialListPlacementAccess {
    @Accessor("placement") SchematicPlacement storage$placement();
}
