package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** A direct reference is remapped by Loom on obfuscated game versions. */
public final class ClientScreenAccess {
    public static Screen get(Minecraft client){return client.screen;}
    private ClientScreenAccess(){}
}
