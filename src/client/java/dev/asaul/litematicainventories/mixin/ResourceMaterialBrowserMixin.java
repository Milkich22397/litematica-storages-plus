package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.ResourceAccounting;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
@Mixin(value=WidgetListMaterialList.class,remap=false)
public abstract class ResourceMaterialBrowserMixin {
    @Shadow @Final private GuiMaterialList gui;
    @Inject(method="getAllEntries",at=@At("HEAD"),cancellable=true)
    private void storages$filter(CallbackInfoReturnable<Collection<MaterialListEntry>> ci){
        if(!ResourceAccounting.combined())return;
        var list=gui.getMaterialList();var ignored=((ResourceMaterialListAccess)list).storages$ignored();
        ci.setReturnValue(list.getMaterialsAll().stream().filter(e->!ignored.contains(e)).filter(e->!list.getHideAvailable()||ResourceAccounting.remaining(e,list)>0).toList());
    }
}
