package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public final class LegacyMessages {
    public static void send(Minecraft client,Component text){if(client.player!=null)client.player.displayClientMessage(text,true);}
    private LegacyMessages(){}
}
