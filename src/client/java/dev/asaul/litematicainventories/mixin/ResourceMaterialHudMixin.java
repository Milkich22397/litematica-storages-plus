package dev.asaul.litematicainventories.mixin;

import dev.asaul.litematicainventories.ResourceAccounting;
import fi.dy.masa.litematica.materials.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import fi.dy.masa.malilib.config.HudAlignment;
import java.util.List;

/** Keep the native HUD layout, but use the same remaining counts as the material table. */
@Mixin(value=MaterialListHudRenderer.class,remap=false)
public abstract class ResourceMaterialHudMixin {
    @Shadow @Final protected MaterialListBase materialList;
    @Shadow protected long lastUpdateTime;
    @Unique private boolean storages$combined;

    @Inject(method="render",at=@At("HEAD"))
    private void storages$modeChanged(GuiGraphicsExtractor g,int x,int y,HudAlignment alignment,CallbackInfoReturnable<Integer> ci){
        boolean combined=ResourceAccounting.combined();
        if(combined!=storages$combined){storages$combined=combined;lastUpdateTime=0;}
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListBase;getMaterialsMissingOnly(Z)Ljava/util/List;"))
    private List<MaterialListEntry> storages$rows(MaterialListBase list,boolean refresh){
        return ResourceAccounting.combined()?ResourceAccounting.hudEntries(list):list.getMaterialsMissingOnly(refresh);
    }

    // The native renderer computes (missing - available) * multiplier in two passes.
    // Combined mode already includes the multiplier and available stock in remaining().
    @Redirect(method="render",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListBase;getMultiplier()I"))
    private int storages$multiplier(MaterialListBase list){return ResourceAccounting.combined()?1:list.getMultiplier();}

    @Redirect(method="render",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountMissing()I"))
    private int storages$missing(MaterialListEntry row){return ResourceAccounting.combined()?ResourceAccounting.remaining(row,materialList):row.getCountMissing();}

    @Redirect(method="render",at=@At(value="INVOKE",target="Lfi/dy/masa/litematica/materials/MaterialListEntry;getCountAvailable()I"))
    private int storages$available(MaterialListEntry row){return ResourceAccounting.combined()?0:row.getCountAvailable();}
}
