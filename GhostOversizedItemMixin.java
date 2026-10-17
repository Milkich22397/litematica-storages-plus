package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.GhostItems;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(PictureInPictureRenderer.class)
public abstract class GhostOversizedItemMixin {
    @ModifyArgs(method="blitTexture",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
    private void storage$alpha(Args args,PictureInPictureRenderState item,GuiRenderState state){
        if(item instanceof OversizedItemRenderState oversized&&GhostItems.contains(oversized.guiItemRenderState()))args.set(11,0x88888888);
    }
}
