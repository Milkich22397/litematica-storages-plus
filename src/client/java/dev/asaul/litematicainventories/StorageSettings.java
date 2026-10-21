package dev.asaul.litematicainventories;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import org.slf4j.LoggerFactory;

public final class StorageSettings {
    public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("litematicainventories");
    public static StorageSettings INSTANCE = load();
    // Legacy aggregate is deliberately ignored by Gson; comparison is always per slot.
    public boolean recording = false, overlay = true;
    public long ttlHours = 168;
    public long cleanupHours = 168;
    public int maxEntries = 50000;
    public String namespace = "default";
    public int missingColor = 0x665599FF, matchColor = 0xFF55CC77, countColor = 0x66FFCC44;
    public int wrongColor = 0x66FF5555, unknownColor = 0xFF999999;
    public int extraColor = 0x66FF77BB;
    public boolean resourceBreakdown=false;
    public boolean resourceTracking=true,includeTrackedResources=false,showTrackingButton=true;
    public record ButtonPosition(int x,int y) {}
    public java.util.Map<String,ButtonPosition> trackingPositions=new java.util.HashMap<>();
    public static boolean arrangeTrackingButton;
    private static StorageSettings load() {
        try {
            Path p = DIRECTORY.resolve("settings.json");
            if (Files.exists(p)) {
                StorageSettings s = new Gson().fromJson(Files.readString(p), StorageSettings.class);
                if (s != null) { if(s.trackingPositions==null)s.trackingPositions=new java.util.HashMap<>(); if(s.missingColor==0xFF5599FF)s.missingColor=0x665599FF; if(s.countColor==0xFFFFCC44)s.countColor=0x66FFCC44; if(s.wrongColor==0xFFFF5555)s.wrongColor=0x66FF5555; s.maxEntries = Math.clamp(s.maxEntries, 1, 200000); s.ttlHours = Math.clamp(s.ttlHours, 0, 876000); s.cleanupHours = Math.clamp(s.cleanupHours, 1, 876000); if (s.namespace == null || s.namespace.isBlank()) s.namespace = "default"; return s; }
            }
        } catch (Exception e) { LoggerFactory.getLogger("StoragePreview").error("Cannot load settings", e); }
        return new StorageSettings();
    }
    public static void save() {
        try { Files.createDirectories(DIRECTORY); Path t = DIRECTORY.resolve("settings.tmp"); Files.writeString(t, new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE)); PersistentInventoryStore.replace(t, DIRECTORY.resolve("settings.json")); }
        catch (Exception e) { LoggerFactory.getLogger("StoragePreview").error("Cannot save settings", e); }
    }
}
