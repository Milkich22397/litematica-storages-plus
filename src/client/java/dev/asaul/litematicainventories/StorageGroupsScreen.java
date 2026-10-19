package dev.asaul.litematicainventories;

import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class StorageGroupsScreen extends GuiBase {
    private final Screen previous;
    private String chosen="",name="";
    public StorageGroupsScreen(Screen parent){previous=parent;setParent(parent);title=TrackedStoragesScreen.tr("storages.groups.manage");useTitleHierarchy=false;}
    private void button(int y,String key,Runnable action){addButton(new ButtonGeneric(12,y,width-24,20,TrackedStoragesScreen.tr(key)),(b,m)->{action.run();initGui();});}
    @Override public void initGui(){super.initGui();String server=StorageGroups.server();var ids=new ArrayList<>(StorageGroups.groups(server).keySet());if(!ids.contains(chosen))chosen=ids.isEmpty()?"":ids.getFirst();
        var text=new GuiTextFieldGeneric(12,32,width-24,18,font);text.setMaxLengthWrapper(64);text.setValueWrapper(name);text.setHint(Component.translatable("storages.groups.name"));addTextField(text,f->{name=f.getValueWrapper();return true;});
        button(58,"storages.groups.create",()->{var id=StorageGroups.create(server,name);if(id!=null)chosen=id;});
        addButton(new ButtonGeneric(12,86,width-24,20,StorageGroups.name(server,chosen)),(b,m)->{if(!ids.isEmpty()){chosen=ids.get((ids.indexOf(chosen)+1)%ids.size());name=StorageGroups.name(server,chosen);initGui();}});
        button(112,"storages.groups.rename",()->StorageGroups.rename(server,chosen,name));
        addButton(new ButtonGeneric(12,138,width-24,20,TrackedStoragesScreen.tr("storages.groups.delete")),(b,m)->{if(!chosen.isEmpty())mc.setScreenAndShow(new ConfirmScreen(yes->{if(yes)StorageGroups.delete(server,chosen);mc.setScreenAndShow(this);},Component.translatable("storages.groups.delete"),Component.translatable("storages.groups.delete.confirm")));});
        button(170,"litematicainventories.done",this::onClose);
    }
    @Override public void onClose(){mc.setScreenAndShow(previous);}
}
