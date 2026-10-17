package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.ResourceAccounting;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GuiBase.class,remap=false)
public abstract class ResourceProgressMixin {
    @Unique private long storages$generation=-1;
    @Inject(method="extractRenderState",at=@At("HEAD"))
    private void storages$refresh(GuiGraphicsExtractor g,int mx,int my,float dt,CallbackInfo ci){
        if((Object)this instanceof GuiMaterialList screen&&ResourceAccounting.combined()&&storages$generation!=ResourceAccounting.generation()){
            storages$generation=ResourceAccounting.generation();((GuiListLayoutAccess)screen).storage$list().refreshEntries();
        }
    }
    @Inject(method="extractRenderState",at=@At("RETURN"))
    private void storages$progress(GuiGraphicsExtractor g,int mx,int my,float dt,CallbackInfo ci){
        if(!((Object)this instanceof GuiMaterialList screen)||!ResourceAccounting.combined())return;
        var p=ResourceAccounting.progress(screen.getMaterialList());
        var text=Component.translatable("storages.progress",p.collected(),p.required(),p.remaining(),String.format(java.util.Locale.ROOT,"%.1f",p.percent()));
        var font=Minecraft.getInstance().font;g.text(font,font.plainSubstrByWidth(text.getString(),Math.max(80,screen.getScreenWidth()-165)),12,screen.getScreenHeight()-36,0xFFFFFFFF,false);
    }
}
