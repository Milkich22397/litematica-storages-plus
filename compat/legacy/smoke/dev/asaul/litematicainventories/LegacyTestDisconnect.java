package dev.asaul.litematicainventories;

final class LegacyTestDisconnect {
    static void disconnect(net.minecraft.client.Minecraft client) {
        // Older clients require the connection to stop the integrated server first.
        client.level.disconnect(net.minecraft.network.chat.Component.literal("Integration reconnect"));
        client.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
    }
}
