package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.GhostItems;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(GuiRenderer.class)
public abstract class GhostItemRendererMixin {
    @ModifyArgs(method="submitBlitFromItemAtlas",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
    private void storage$alpha(Args args,GuiItemRenderState item,GuiItemAtlas.SlotView slot){
        // The item atlas uses premultiplied alpha: RGB must fade together with alpha.
        if(GhostItems.contains(item))args.set(11,0x88888888);
    }
}
