package dev.asaul.litematicainventories;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Extraction and atlas preparation run on the client thread. Weak keys expire with the frame. */
public final class GhostItems {
    public static boolean extracting;
    private static final Set<GuiItemRenderState> ghosts=Collections.newSetFromMap(new WeakHashMap<>());
    public static void capture(GuiItemRenderState item){if(extracting)ghosts.add(item);}
    public static boolean contains(GuiItemRenderState item){return ghosts.contains(item);}
    private GhostItems(){}
}
