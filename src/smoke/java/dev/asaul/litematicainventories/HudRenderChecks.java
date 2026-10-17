package dev.asaul.litematicainventories;

import fi.dy.masa.litematica.materials.*;
import fi.dy.masa.malilib.config.HudAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Exercises the actual transformed native renderer, including its width and draw passes. */
public final class HudRenderChecks extends Screen {
    public final MaterialListBase list;
    public final Probe hud;
    public int renderedHeight;
    public HudRenderChecks(MaterialListBase list){super(Component.literal("HUD integration"));this.list=list;hud=new Probe(list);}
    public static final class Probe extends MaterialListHudRenderer {
        public final List<Integer> counts=new ArrayList<>();
        Probe(MaterialListBase list){super(list);}
        @Override protected String getFormattedCountString(int count,int stackSize){counts.add(count);return super.getFormattedCountString(count,stackSize);}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        hud.counts.clear();renderedHeight=hud.render(g,8,8,HudAlignment.TOP_LEFT);
    }
    public void expect(int count){
        if(hud.counts.size()!=2||hud.counts.stream().anyMatch(n->n!=count))throw new AssertionError("HUD expected "+count+" in both native passes, got "+hud.counts);
    }
}
