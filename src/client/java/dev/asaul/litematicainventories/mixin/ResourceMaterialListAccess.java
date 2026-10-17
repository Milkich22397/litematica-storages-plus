package dev.asaul.litematicainventories.mixin;
import fi.dy.masa.litematica.materials.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;
@Mixin(value=MaterialListBase.class,remap=false)
public interface ResourceMaterialListAccess {
    @Accessor("ignored") Set<MaterialListEntry> storages$ignored();
}
