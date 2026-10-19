package dev.asaul.litematicainventories.mixin;

import dev.asaul.litematicainventories.ResourceListRevision;
import fi.dy.masa.litematica.materials.MaterialListBase;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MaterialListBase.class,remap=false)
public abstract class ResourceListRevisionMixin implements ResourceListRevision {
    @Unique private long storages$revision;
    @Override public long storages$revision(){return storages$revision;}
    @Inject(method={"setMaterialListEntries","ignoreEntry","clearIgnored","setMultiplier","setSortCriteria","refreshPreFilteredList","fromJson"},at=@At("RETURN"))
    private void storages$changed(CallbackInfo ci){storages$revision++;}
}
