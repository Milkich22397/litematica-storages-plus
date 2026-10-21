package dev.asaul.litematicainventories;
import net.minecraft.client.gui.screens.Screen;
public final class DataSettingsScreen extends TrackedStoragesScreen {
    public DataSettingsScreen(Screen parent,java.util.List<CacheKey> keys){super(parent,fi.dy.masa.litematica.data.DataManager.getMaterialList(),2,keys);}
}
