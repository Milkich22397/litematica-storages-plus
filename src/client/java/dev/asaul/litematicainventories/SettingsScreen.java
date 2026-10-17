package dev.asaul.litematicainventories;
import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import fi.dy.masa.litematica.data.DataManager;
import java.util.*;
public final class SettingsScreen extends GuiBase {
    private final Screen previous;
    private final List<CacheKey> containerKeys;
    public SettingsScreen(Screen parent){previous=parent;setParent(parent);title="Litematica Storages+";useTitleHierarchy=false;var o=ContainerRecorder.active();containerKeys=o==null?List.of():o.adapter.halves().stream().map(p->new CacheKey(o.server,o.dimension,p)).toList();}
    private void button(int x,int y,int w,String key,Runnable action){addButton(new ButtonGeneric(x,y,w,20,Component.translatable(key).getString()),(b,m)->{action.run();StorageSettings.save();if(mc.gui.screen()==this)initGui();});}
    @Override public void initGui(){
        super.initGui();int total=Math.min(500,width-24),x=(width-total)/2,col=(total-8)/2;var s=StorageSettings.INSTANCE;
        button(x,24,total,"storages.settings",()->mc.setScreenAndShow(new ResourceSettingsScreen(this)));
        button(x,48,col,s.recording?"litematicainventories.recording.on":"litematicainventories.recording.off",SessionInventoryCache::toggleRecording);
        button(x+col+8,48,col,s.overlay?"storage.overlay.on":"storage.overlay.off",()->s.overlay=!s.overlay);
        button(x,70,total,"storage.mode."+(s.aggregate?"aggregate":"strict"),()->s.aggregate=!s.aggregate);
        var profile=new GuiTextFieldGeneric(x,94,total,16,font);profile.setMaxLengthWrapper(100);profile.setValueWrapper(s.namespace);profile.setHoverTooltip(Component.translatable("storage.profile").getString());addTextField(profile,f->{if(!f.getValueWrapper().isBlank())s.namespace=f.getValueWrapper();return true;});
        addLabel(x,114,col,12,-1,Component.translatable("storage.ttl").getString());addLabel(x+col+8,114,col,12,-1,Component.translatable("storage.limit").getString());
        var ttl=new GuiTextFieldGeneric(x,128,col,16,font);ttl.setValueWrapper(Long.toString(s.ttlHours));addTextField(ttl,f->{try{s.ttlHours=Math.clamp(Long.parseLong(f.getValueWrapper()),0,876000);}catch(NumberFormatException invalid){return false;}return true;});
        var limit=new GuiTextFieldGeneric(x+col+8,128,col,16,font);limit.setValueWrapper(Integer.toString(s.maxEntries));addTextField(limit,f->{try{s.maxEntries=Math.clamp(Integer.parseInt(f.getValueWrapper()),1,200000);}catch(NumberFormatException invalid){return false;}return true;});
        button(x,150,col,"storage.clear.container",()->{containerKeys.forEach(SessionInventoryCache::remove);SessionInventoryCache.flush();});
        button(x+col+8,150,col,"storage.clear.world",SessionInventoryCache::clearWorld);
        button(x,172,col,"storage.clear.all",SessionInventoryCache::clear);
        button(x+col+8,172,col,"storage.placement",()->{var m=DataManager.getSchematicPlacementManager();var list=m.getAllSchematicsPlacements().stream().filter(p->p.isEnabled()).sorted(Comparator.comparing(p->p.getHashId().toString())).toList();if(!list.isEmpty()){int i=list.indexOf(m.getSelectedSchematicPlacement());m.setSelectedSchematicPlacement(list.get((i+1)%list.size()));}});
        button(x,194,col,"storage.colors",()->mc.setScreenAndShow(new ColorSettingsScreen(this)));
        button(x+col+8,194,col,"litematicainventories.done",this::onClose);
        if(!PersistentInventoryStore.error.isBlank())addLabel(x,218,total,12,0xFFFF7777,PersistentInventoryStore.error);
    }
    @Override public void onClose(){StorageSettings.save();mc.setScreenAndShow(previous);}
}
