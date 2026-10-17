package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.ContainerRecorder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method="handleContainerContent",at=@At("RETURN"))
    private void storage$full(ClientboundContainerSetContentPacket p,CallbackInfo ci) { ContainerRecorder.received(p.containerId(),true); }
    @Inject(method="handleContainerSetSlot",at=@At("RETURN"))
    private void storage$slot(ClientboundContainerSetSlotPacket p,CallbackInfo ci) { ContainerRecorder.received(p.getContainerId(),false); }
}
