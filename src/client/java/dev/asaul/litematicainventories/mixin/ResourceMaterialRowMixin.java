package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.ResourceAccounting;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=WidgetMaterialListEntry.class,remap=false)
public abstract class ResourceMaterialRowMixin {
    @Shadow @Final private MaterialListEntry entry;
    @Shadow @Final private MaterialListBase materialList;
    @Redirect(method={"render","setMaxNameLength"},at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountAvailable()I"))
    private static int storages$available(MaterialListEntry row){return ResourceAccounting.displayed(row);}
    @ModifyVariable(method="render",at=@At("STORE"),name="countMissing")
    private int storages$remaining(int original){return ResourceAccounting.combined()&&entry!=null?ResourceAccounting.remaining(entry,materialList):original;}
    @ModifyVariable(method="postRenderHovered",at=@At("STORE"),name="missing")
    private int storages$hoverRemaining(int original){return ResourceAccounting.combined()&&entry!=null?ResourceAccounting.remaining(entry,materialList):original;}
}
