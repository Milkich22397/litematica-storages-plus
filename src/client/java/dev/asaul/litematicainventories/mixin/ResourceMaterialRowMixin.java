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
    @Redirect(method="render",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountAvailable()I"))
    private int storages$available(MaterialListEntry row){return ResourceAccounting.displayed(row,materialList);}
    @Redirect(method="setMaxNameLength",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountAvailable()I"))
    private static int storages$width(MaterialListEntry row){return ResourceAccounting.displayWidthCount(row);}
    @Inject(method="postRenderHovered",at=@At("HEAD"),cancellable=true)
    private void storages$breakdown(fi.dy.masa.malilib.render.GuiContext ctx,int x,int y,boolean selected,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(entry==null||!dev.asaul.litematicainventories.StorageSettings.INSTANCE.resourceBreakdown||!ResourceAccounting.combined())return;
        var b=ResourceAccounting.breakdown(entry.getStack(),materialList);
        var lines=java.util.List.of(entry.getStack().getHoverName(),net.minecraft.network.chat.Component.translatable("storages.breakdown.available",b.total()),net.minecraft.network.chat.Component.translatable("storages.breakdown.inventory",b.inventory()),net.minecraft.network.chat.Component.translatable("storages.breakdown.ender",b.ender()),net.minecraft.network.chat.Component.translatable("storages.breakdown.storages",b.storages()),net.minecraft.network.chat.Component.translatable("storages.breakdown.remaining",ResourceAccounting.remaining(entry,materialList)));
        ctx.getGuiGraphics().setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font,lines,java.util.Optional.empty(),x,y);ci.cancel();
    }
    @ModifyVariable(method="render",at=@At("STORE"),name="countMissing")
    private int storages$remaining(int original){return ResourceAccounting.combined()&&entry!=null?ResourceAccounting.remaining(entry,materialList):original;}
    @ModifyVariable(method="postRenderHovered",at=@At("STORE"),name="missing")
    private int storages$hoverRemaining(int original){return ResourceAccounting.combined()&&entry!=null?ResourceAccounting.remaining(entry,materialList):original;}
}
