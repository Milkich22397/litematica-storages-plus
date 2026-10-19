package dev.asaul.litematicainventories;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.SharedConstants;
import java.util.*;

public final class ContainerRecorder {
    private record Candidate(ClientLevel world,BlockPos pos,BlockState state,long time) {}
    public static final class Opening {
        public final ClientLevel world; public final AbstractContainerMenu menu; public final ContainerMenuAdapter adapter;
        public final String server,dimension; public final List<BlockState> states; public final long created=System.currentTimeMillis();
        public final UUID playerId;
        public final TrackedStorageStore.Key trackingKey;
        public boolean synchronizedMenu;
        private List<ItemStack> previous;
        private List<ItemStack> observedItems;
        List<ItemStack> resourcePrevious;
        Opening(ClientLevel w,AbstractContainerMenu m,ContainerMenuAdapter a) {world=w;menu=m;adapter=a; var c=Minecraft.getInstance();playerId=c.player.getUUID();server=SessionContext.serverId(c); dimension=SessionContext.dimensionId(c); states=new ArrayList<>(a.halves().stream().map(w::getBlockState).toList());trackingKey=TrackedStorageStore.createKey(this);}
    }
    private static Candidate candidate;
    private static Opening active;
    public static Opening active() { return active; }
    public static void disconnected(){active=null;candidate=null;status="";StorageOverlayRenderer.reset();}
    public static String status="";
    public static void initialize() {
        UseBlockCallback.EVENT.register((p,w,h,hit)-> { if(h==InteractionHand.MAIN_HAND && w instanceof ClientLevel cw) candidate=new Candidate(cw,hit.getBlockPos().immutable(),cw.getBlockState(hit.getBlockPos()),System.currentTimeMillis()); return InteractionResult.PASS; });
        ScreenEvents.AFTER_INIT.register((c,s,w,h)-> {
            if(!(s instanceof AbstractContainerScreen<?> screen)) return;
            if(active!=null && active.menu==screen.getMenu()) return; // GUI resize
            Candidate hit=candidate; candidate=null; active=null; status="";
            if(hit==null||hit.world!=c.level||System.currentTimeMillis()-hit.time>2000||c.player==null||c.player.blockPosition().distSqr(hit.pos)>100||!c.level.getBlockState(hit.pos).equals(hit.state)) return;
            ContainerMenuAdapter a=ContainerMenuAdapter.create(screen.getMenu(),hit.pos,hit.state);
            if(a==null) { status="unsupported"; ScreenEvents.remove(s).register(removed->status=""); return; }
            for(BlockPos pos:a.halves()) if(!ClientChunks.loaded(c.level,pos)||c.level.getBlockEntity(pos)==null) return;
            Opening opening=new Opening(c.level,screen.getMenu(),a); active=opening;
            StorageLocator.opened(opening);
            ScreenEvents.remove(s).register(removed->{ if(active==opening) { snapshotMenu(opening); active=null; SessionInventoryCache.flush(); TrackedStorageStore.flush(); } });
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(candidate!=null&&(candidate.world!=c.level||System.currentTimeMillis()-candidate.time>2000))candidate=null;
            if(active!=null && (active.world!=c.level || c.player==null || c.player.containerMenu!=active.menu || !SessionContext.serverId(c).equals(active.server))) { active=null; candidate=null; SessionInventoryCache.flush(); }
            if(active!=null) received(active.menu.containerId,false);
        });
    }
    private static boolean validate(Opening o) {
        for(int i=0;i<o.adapter.halves().size();i++) {
            if(!ClientChunks.loaded(o.world,o.adapter.halves().get(i))){active=null;return false;}
            var current=o.world.getBlockState(o.adapter.halves().get(i));
            if(!ContainerMenuAdapter.identityState(current).equals(ContainerMenuAdapter.identityState(o.states.get(i)))){for(BlockPos p:o.adapter.halves())SessionInventoryCache.remove(new CacheKey(o.server,o.dimension,p));active=null;return false;}
            if(!current.equals(o.states.get(i))){o.states.set(i,current);o.previous=null;}
        }
        return true;
    }
    /** Invoked only AFTER vanilla has applied a server packet on the client thread. */
    public static void received(int id,boolean full) {
        Opening o=active; var c=Minecraft.getInstance();
        if(o==null||o.world!=c.level||c.player==null||c.player.containerMenu!=o.menu||id!=o.menu.containerId) return;
        if(full) o.synchronizedMenu=true;
        snapshotMenu(o);
    }
    /** Vanilla may suppress an echo for an accepted predicted click. Read only menus
     * whose initial full packet has been applied; later corrections overwrite this observation. */
    private static void snapshotMenu(Opening o) {
        if(!o.synchronizedMenu||Minecraft.getInstance().level!=o.world||!validate(o)) return;
        boolean resources=StorageSettings.INSTANCE.resourceTracking&&TrackedStorageStore.tracked(o);
        boolean schematic=SessionInventoryCache.isRecording()&&!TrackedStorageStore.personal(o);
        if(!resources&&!schematic)return;
        if(!o.adapter.matchesItems(o.observedItems))o.observedItems=o.adapter.copyItems();
        if(resources)TrackedStorageStore.observe(o,o.observedItems);
        if(!schematic)return;
        try { writeMenu(o,o.observedItems); }
        catch(RuntimeException e) {
            PersistentInventoryStore.error="Inventory serialization failed: "+e.getMessage();
            org.slf4j.LoggerFactory.getLogger("StoragePreview").error(PersistentInventoryStore.error,e);
            var c=Minecraft.getInstance();if(c.player!=null)c.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("storage.serialization_error"));
        }
    }
    private static void writeMenu(Opening o,List<ItemStack> items) {
        if(o.previous!=null && ItemStack.listMatches(o.previous,items)) return;
        for(int i=0;i<o.adapter.halves().size();i++) {
            BlockPos pos=o.adapter.halves().get(i); var be=o.world.getBlockEntity(pos); if(be==null) continue;
            int size=o.adapter.halfSize(); CompoundTag tag=InventoryData.encode(items.subList(i*size,(i+1)*size),o.world.registryAccess());
            tag.putString("id",BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
            SessionInventoryCache.put(new CacheKey(o.server,o.dimension,pos),new ContainerSnapshot(BuiltInRegistries.BLOCK.getKey(o.states.get(i).getBlock()).toString(),o.states.get(i).toString(),size,System.currentTimeMillis(),SharedConstants.getCurrentVersion().dataVersion().version(),tag));
        }
        o.previous=items;
    }
    public static void recordingChanged() { if(active!=null) active.previous=null; }
}
