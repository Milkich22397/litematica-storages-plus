package dev.asaul.litematicainventories.mixin;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Preserve our root namespace across reads and writes without interfering with vanilla Items. */
@Mixin(value=LitematicaSchematic.class,remap=false)
public abstract class SchematicMetadataMixin {
    @Unique private CompoundData storage$metadata=new CompoundData();
    @Inject(method="readFromData",at=@At("HEAD"))
    private void storage$read(CompoundData data,CallbackInfoReturnable<Boolean> ci) {storage$metadata=data.getCompound("litematicainventories:storage").copy();}
    @Inject(method="writeToData",at=@At("RETURN"))
    private void storage$write(CallbackInfoReturnable<CompoundData> ci) {
        if(storage$metadata==null)storage$metadata=new CompoundData();
        storage$metadata.putInt("Schema",1);ci.getReturnValue().put("litematicainventories:storage",storage$metadata.copy());
    }
}
