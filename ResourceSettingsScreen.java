package dev.asaul.litematicainventories;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
public final class ResourceSettingsScreen extends GuiBase {
    private final Screen previous;
    public ResourceSettingsScreen(Screen parent){previous=parent;setParent(parent);title="Litematica Storages+";useTitleHierarchy=false;}
    private void button(int y,String key,Runnable action){int w=Math.min(390,width-24);addButton(new ButtonGeneric((width-w)/2,y,w,20,Component.translatable(key).getString()),(b,m)->{action.run();StorageSettings.save();if(mc.gui.screen()==this)initGui();});}
    @Override public void initGui(){super.initGui();var s=StorageSettings.INSTANCE;
        button(28,"storages.enabled."+s.resourceTracking,()->{s.resourceTracking=!s.resourceTracking;ResourceAccounting.tick(mc);});
        button(50,"storages.mode."+s.includeTrackedResources,()->{s.includeTrackedResources=!s.includeTrackedResources;ResourceAccounting.tick(mc);});
        button(72,"storages.button."+s.showTrackingButton,()->s.showTrackingButton=!s.showTrackingButton);
        button(94,"storages.arrange."+StorageSettings.arrangeTrackingButton,()->StorageSettings.arrangeTrackingButton=!StorageSettings.arrangeTrackingButton);
        button(116,"storages.reset_position",()->{s.trackingPositions.clear();StorageSettings.arrangeTrackingButton=false;});
        button(138,"storages.clear",()->mc.setScreenAndShow(new ConfirmScreen(yes->{if(yes){TrackedStorageStore.clear();ResourceAccounting.tick(mc);}mc.setScreenAndShow(this);},Component.translatable("storages.clear"),Component.translatable("storages.clear.confirm"))));
        button(166,"litematicainventories.done",this::onClose);
        if(!TrackedStorageStore.error.isBlank())addLabel(12,192,width-24,30,0xFFFF7777,TrackedStorageStore.error);
    }
    @Override public void onClose(){StorageSettings.save();mc.setScreenAndShow(previous);}
}
