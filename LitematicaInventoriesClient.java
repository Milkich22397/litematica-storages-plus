package dev.asaul.litematicainventories;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class LitematicaInventoriesClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath("litematicainventories", "main")
    );

    private static final KeyMapping TOGGLE_RECORDING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.litematicainventories.toggle_recording", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F8, CATEGORY
    ));
    private static final KeyMapping CLEAR_CACHE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.litematicainventories.clear_cache", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F9, CATEGORY
    ));
    private static final KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.litematicainventories.open_settings", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F10, CATEGORY
    ));

    private static final KeyMapping TOGGLE_MODE=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.litematicainventories.toggle_mode",InputConstants.Type.KEYBOARD,InputConstants.KEY_F7,CATEGORY));
    @Override
    public void onInitializeClient() {
        SessionInventoryCache.size();
        TrackedStorageStore.load();
        ContainerRecorder.initialize();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c -> { TrackedStorageStore.awaitWrites(); SessionInventoryCache.shutdown(); });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c) -> {SessionInventoryCache.flush();TrackedStorageStore.flush();});
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SessionInventoryCache.tick();
            TrackedStorageStore.tick(client);
            ResourceAccounting.tick(client);
            while(TOGGLE_MODE.consumeClick()){StorageSettings.INSTANCE.aggregate=!StorageSettings.INSTANCE.aggregate;StorageSettings.save();if(client.player!=null)client.player.sendOverlayMessage(Component.translatable("storage.mode."+(StorageSettings.INSTANCE.aggregate?"aggregate":"strict")));}
            while (TOGGLE_RECORDING.consumeClick()) {
                SessionInventoryCache.toggleRecording();
            }
            while (CLEAR_CACHE.consumeClick()) {
                SessionInventoryCache.clearWorld();
            }
            while (OPEN_SETTINGS.consumeClick()) {
                var settings=new SettingsScreen(null); // Capture the container clear scope before closing it.
                if(client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && client.player!=null)client.player.closeContainer();
                client.setScreenAndShow(settings);
            }
        });
    }
}
