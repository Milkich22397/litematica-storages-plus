package dev.asaul.litematicainventories.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import com.google.common.collect.ImmutableList;
@Mixin(value=MaterialListSchematic.class,remap=false)
public interface MaterialListSchematicAccess {
    @Accessor("schematic") LitematicaSchematic storage$schematic();
    @Accessor("regions") ImmutableList<String> storage$regions();
}
