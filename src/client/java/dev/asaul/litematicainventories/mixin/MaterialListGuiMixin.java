package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.*;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
@Mixin(value=GuiMaterialList.class,remap=false)
public abstract class MaterialListGuiMixin {
    @Unique private StorageMaterialScreen storage$tab;
    @Unique private int storage$extraRows;
    @Inject(method="getElementTotalWidth",at=@At("RETURN"),cancellable=true)
    private void storage$reserveButton(CallbackInfoReturnable<Integer> ci){ci.setReturnValue(ci.getReturnValue()+Minecraft.getInstance().font.width(Component.translatable("storage.tab"))+Minecraft.getInstance().font.width(Component.translatable("storages.mode."+StorageSettings.INSTANCE.includeTrackedResources))+Minecraft.getInstance().font.width(Component.translatable("storages.manager"))+36);}
    @Inject(method="getBrowserHeight",at=@At("RETURN"),cancellable=true)
    private void storage$listHeight(CallbackInfoReturnable<Integer> ci){ci.setReturnValue(ci.getReturnValue()-storage$extraRows*22);}
    @Inject(method="createCounts",at=@At("HEAD"),cancellable=true)
    private void storages$resourceFooter(CallbackInfo ci){if(ResourceAccounting.combined())ci.cancel();}
    @Inject(method="initGui",at=@At("HEAD"))
    private void storage$resetLayout(CallbackInfo ci){if(storage$extraRows>0){storage$extraRows=0;var l=(GuiListLayoutAccess)this;l.storage$position(10,44);l.storage$recreate();}}
    @Inject(method="initGui",at=@At("RETURN"))
    private void storage$buttons(CallbackInfo ci){
        var self=(GuiMaterialList)(Object)this;int x=12,y=24;
        for(var b:((GuiBaseButtonsAccess)self).storage$buttons())if(b.getY()==24)x=Math.max(x,b.getX()+b.getWidth()+2);
        int actions=StorageMaterialList.supports(self.getMaterialList())?3:2;
        for(int n=0;n<actions;n++){
            boolean mode=n==actions-1,manager=n==actions-2;String key=mode?"storages.mode."+StorageSettings.INSTANCE.includeTrackedResources:manager?"storages.manager":"storage.tab";
            int w=Math.min(self.getScreenWidth()-24,Minecraft.getInstance().font.width(Component.translatable(key))+10);
            if(x+w>(y==24?self.getScreenWidth()-155:self.getScreenWidth()-12)){x=12;y+=22;}
            var button=new ButtonGeneric(x,y,w,20,Component.translatable(key).getString());
            if(mode){button.setEnabled(StorageSettings.INSTANCE.resourceTracking);self.addButton(button,(b,m)->{StorageSettings.INSTANCE.includeTrackedResources=!StorageSettings.INSTANCE.includeTrackedResources;StorageSettings.save();ResourceAccounting.tick(Minecraft.getInstance());self.getMaterialList().refreshPreFilteredList();self.initGui();});}
            else if(manager)self.addButton(button,(b,m)->Minecraft.getInstance().setScreenAndShow(new TrackedStoragesScreen(self,self.getMaterialList())));
            else self.addButton(button,(b,m)->{if(storage$tab==null)storage$tab=new StorageMaterialScreen(self,new StorageMaterialList(self.getMaterialList()));Minecraft.getInstance().setScreenAndShow(storage$tab);});
            x+=w+2;
        }
        storage$extraRows=(y-24)/22;
        if(storage$extraRows>0){var l=(GuiListLayoutAccess)self;l.storage$position(10,44+storage$extraRows*22);l.storage$recreate();l.storage$list().initGui();}
    }
}
