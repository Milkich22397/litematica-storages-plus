package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.GhostItems;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderState.class)
public abstract class GhostItemCaptureMixin {
    @Inject(method="addItem",at=@At("HEAD"))
    private void storage$capture(GuiItemRenderState item,CallbackInfo ci){GhostItems.capture(item);}
}
