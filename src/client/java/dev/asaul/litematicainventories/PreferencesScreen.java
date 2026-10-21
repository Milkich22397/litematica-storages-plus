package dev.asaul.litematicainventories;
import net.minecraft.client.gui.screens.Screen;
public final class PreferencesScreen extends TrackedStoragesScreen {
    public PreferencesScreen(Screen parent){super(parent,fi.dy.masa.litematica.data.DataManager.getMaterialList(),1,null);}
}
