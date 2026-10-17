package dev.asaul.litematicainventories.mixin;

import com.google.common.collect.ImmutableMap;
import dev.asaul.litematicainventories.SessionContext;
import dev.asaul.litematicainventories.SessionInventoryCache;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.scheduler.tasks.TaskSaveSchematic;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TaskSaveSchematic.class, remap = false)
public abstract class TaskSaveSchematicMixin {
    @Shadow @Final private LitematicaSchematic schematic;
    @Shadow @Final private ImmutableMap<String, Box> subRegions;
    @Shadow @Final protected boolean fromSchematicWorld;

    @org.spongepowered.asm.mixin.Unique private String storage$server, storage$dimension;
    @org.spongepowered.asm.mixin.Unique private java.util.Map<dev.asaul.litematicainventories.CacheKey,dev.asaul.litematicainventories.ContainerSnapshot> storage$cache;
    @Inject(method="<init>(Ljava/nio/file/Path;Ljava/lang/String;Lfi/dy/masa/litematica/schematic/LitematicaSchematic;Lfi/dy/masa/litematica/selection/AreaSelection;Lfi/dy/masa/litematica/schematic/LitematicaSchematic$SchematicSaveInfo;Z)V",at=@At("RETURN"))
    private void storage$captureContext(CallbackInfo ci) { var c=Minecraft.getInstance(); storage$server=SessionContext.serverId(c); storage$dimension=SessionContext.dimensionId(c); storage$cache=SessionInventoryCache.snapshot(); }

    @Inject(
            method = "onStop",
            at = @At(
                    value = "INVOKE",
                    target = "Lfi/dy/masa/litematica/schematic/SchematicMetadata;setTimeCreated(J)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void litematicainventories$injectSessionInventories(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!this.fromSchematicWorld && storage$cache != null) {
            SessionInventoryCache.injectInto(
                    this.schematic,
                    this.subRegions,
                    storage$server,
                    storage$dimension, storage$cache
            );
        }
    }
}

