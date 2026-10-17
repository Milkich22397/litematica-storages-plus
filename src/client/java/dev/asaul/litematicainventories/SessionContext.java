package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;
import java.util.Locale;
public final class SessionContext {
    private SessionContext() {}
    public static String serverId(Minecraft c) {
        String base;
        if(c.hasSingleplayerServer() && c.getSingleplayerServer()!=null) base="save:"+c.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if(c.getCurrentServer()!=null) { var a=ServerAddress.parseString(c.getCurrentServer().ip); base="server:["+a.getHost().toLowerCase(Locale.ROOT)+"]:"+a.getPort(); }
        else base="unbound";
        return base+"|profile:"+StorageSettings.INSTANCE.namespace;
    }
    public static String dimensionId(Minecraft c) { return c.level==null?"unknown":c.level.dimension().identifier().toString(); }
}
