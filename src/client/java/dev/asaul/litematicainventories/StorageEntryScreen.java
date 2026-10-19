package dev.asaul.litematicainventories;

import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class StorageEntryScreen extends GuiBase {
    private final Screen previous;
    private final TrackedStorageStore.Key key;
    private String label,group;
    public StorageEntryScreen(Screen parent,TrackedStorageStore.Key key){previous=parent;this.key=key;var e=TrackedStorageStore.get(key);label=e==null?"":e.label();group=e==null?"":e.group();setParent(parent);title=TrackedStoragesScreen.tr("storages.edit");useTitleHierarchy=false;}
    @Override public void initGui(){super.initGui();int x=12,w=width-24;var e=TrackedStorageStore.get(key);if(e==null){onClose();return;}
        addLabel(x,30,w,12,-1,TrackedStoragesScreen.name(e));addLabel(x,47,w,12,0xFFAAAAAA,TrackedStoragesScreen.coordinates(e));
        var name=new GuiTextFieldGeneric(x,70,w,18,font);name.setMaxLengthWrapper(80);name.setValueWrapper(label);name.setHint(Component.translatable("storages.label"));addTextField(name,f->{label=f.getValueWrapper();return true;});
        addButton(new ButtonGeneric(x,98,w,20,TrackedStoragesScreen.tr("storages.manager.filter",StorageGroups.name(key.server(),group))),(b,m)->{var ids=new ArrayList<>(StorageGroups.choices(key.server()));ids.remove(StorageGroups.ALL);group=ids.get((ids.indexOf(group)+1)%ids.size());initGui();});
        addButton(new ButtonGeneric(x,126,w,20,TrackedStoragesScreen.tr("storages.save")),(b,m)->{TrackedStorageStore.edit(key,label,group);ResourceAccounting.tick(mc);onClose();});
        addButton(new ButtonGeneric(x,152,w,20,Component.translatable("gui.cancel").getString()),(b,m)->onClose());
    }
    @Override public void onClose(){mc.setScreenAndShow(previous);}
}
