package dev.asaul.litematicainventories;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.profiling.ProfilerFiller;

/** A single explicit target, bounded lifetime, using MaLiLib's through-wall outline pipeline. */
public final class StorageLocator implements IRenderer {
    private static TrackedStorageStore.Key target;
    private static final long LIFETIME=15_000_000_000L;
    private static final long FADE_IN=250_000_000L,FADE_OUT=350_000_000L;
    private static long shownAt,expires,fadeStarted;
    private static float fadeFrom;
    private static boolean stopping;
    public static void initialize(){fi.dy.masa.malilib.event.RenderEventHandler.getInstance().registerWorldLastRenderer(new StorageLocator());}
    /** Forget a target immediately when the world or its observation is no longer valid. */
    public static void reset(){target=null;shownAt=expires=fadeStarted=0;fadeFrom=0;stopping=false;}
    /** Deselect immediately, retaining only the short visual fade-out. */
    public static void clear(){if(target==null||stopping)return;long now=System.nanoTime();fadeFrom=opacity(now);fadeStarted=now;stopping=true;}
    public static TrackedStorageStore.Key target(){return stopping?null:target;}
    public static boolean canShow(TrackedStorageStore.Entry e){var c=Minecraft.getInstance();return e!=null&&c.level!=null&&c.player!=null&&e.valid()&&!e.personal()&&e.key().server().equals(SessionContext.serverId(c))&&e.key().dimension().equals(SessionContext.dimensionId(c));}
    public static boolean show(TrackedStorageStore.Entry e){if(!canShow(e))return false;target=e.key();shownAt=System.nanoTime();expires=shownAt+LIFETIME;stopping=false;fadeStarted=0;fadeFrom=0;var c=Minecraft.getInstance();var p=e.positions().getFirst();c.player.sendOverlayMessage(Component.translatable("storages.locate.target",p.getX(),p.getY(),p.getZ()));return true;}
    /** Called after a supported container screen actually opens, not on a blocked use attempt. */
    public static void opened(ContainerRecorder.Opening opening){if(target!=null&&target.equals(opening.trackingKey))clear();}
    private static float smooth(long elapsed,long duration){float t=Math.clamp((float)(elapsed/(double)duration),0f,1f);return t*t*(3f-2f*t);}
    static float opacity(long now){
        if(target==null)return 0;
        if(stopping)return fadeFrom*(1f-smooth(now-fadeStarted,FADE_OUT));
        return smooth(now-shownAt,FADE_IN)*smooth(expires-now,FADE_OUT);
    }
    @Override public void onExtractWorldLast(DeltaTracker delta,Camera camera,float tick,ProfilerFiller profiler){
        if(target==null)return;var entry=TrackedStorageStore.get(target);
        long now=System.nanoTime();
        if(entry==null||!canShow(entry)||(stopping?now-fadeStarted>=FADE_OUT:now>=expires)){reset();return;}
        float alpha=opacity(now);if(alpha<=0)return;
        var color=new Color4f(0.25f,0.85f,1f,alpha);
        for(var pos:entry.positions())RenderUtils.scheduleBlockOutline(pos,0.005f,3f,color,true);
    }
}
