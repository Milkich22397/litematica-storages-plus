package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.ResourceAccounting;
import fi.dy.masa.litematica.materials.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=MaterialListSorter.class,remap=false)
public abstract class ResourceMaterialSorterMixin {
    @Shadow @Final private MaterialListBase materialList;
    @Redirect(method="compare(Lfi/dy/masa/litematica/materials/MaterialListEntry;Lfi/dy/masa/litematica/materials/MaterialListEntry;)I",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountAvailable()I"))
    private int storages$available(MaterialListEntry row){return ResourceAccounting.displayed(row);}
    @Redirect(method="compare(Lfi/dy/masa/litematica/materials/MaterialListEntry;Lfi/dy/masa/litematica/materials/MaterialListEntry;)I",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountMissing()I"))
    private int storages$remaining(MaterialListEntry row){return ResourceAccounting.combined()?ResourceAccounting.remaining(row,materialList):row.getCountMissing();}
}
