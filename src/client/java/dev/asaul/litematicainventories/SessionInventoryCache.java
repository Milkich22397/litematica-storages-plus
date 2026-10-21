package dev.asaul.litematicainventories;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import fi.dy.masa.malilib.util.data.tag.util.DataTypeUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public final class SessionInventoryCache {
    private static final Map<CacheKey, ContainerSnapshot> CACHE = new HashMap<>();
    private static java.util.concurrent.CompletableFuture<Map<CacheKey,ContainerSnapshot>> LOADING=PersistentInventoryStore.loadAsync();
    private static boolean loaded,clearedAll;
    private static final java.util.Set<String> clearedWorlds=new java.util.HashSet<>();
    private static final java.util.Set<CacheKey> removed=new java.util.HashSet<>();
    private static void loadReady(boolean wait) {
        if(loaded || !wait && !LOADING.isDone())return;
        try {
            var data=wait?LOADING.get(10,java.util.concurrent.TimeUnit.SECONDS):LOADING.join();
            if(!clearedAll) data.forEach((k,v)->{if(!clearedWorlds.contains(k.server())&&!removed.contains(k))CACHE.putIfAbsent(k,v);});
            loaded=true;
            if(trim())dirty(false);
        }catch(Exception e){PersistentInventoryStore.error="Cache load failed: "+e.getMessage();loaded=true;}
        finally{LOADING=null;removed.clear();clearedWorlds.clear();clearedAll=false;}
    }
    

    private SessionInventoryCache() {}

    public static boolean isRecording() {
        return StorageSettings.INSTANCE.recording;
    }

    public static void toggleRecording() {
        StorageSettings.INSTANCE.recording = !StorageSettings.INSTANCE.recording;
        StorageSettings.save();
        ContainerRecorder.recordingChanged();
        showActionBar(isRecording() ? "litematicainventories.enabled" : "litematicainventories.disabled");
    }

    public static void put(CacheKey key, ContainerSnapshot snapshot) {
        CACHE.put(key, snapshot);
        trim();
        dirty(false);
    }

    /** A reduced limit must not perform one full scan for every discarded observation. */
    private static boolean trim(){
        int excess=CACHE.size()-StorageSettings.INSTANCE.maxEntries;
        if(excess<=0)return false;
        var order=java.util.Comparator.<Map.Entry<CacheKey,ContainerSnapshot>>comparingLong(e->e.getValue().observedAt());
        if(excess==1)CACHE.remove(CACHE.entrySet().stream().min(order).orElseThrow().getKey());
        else CACHE.entrySet().stream().sorted(order).limit(excess).map(Map.Entry::getKey).toList().forEach(CACHE::remove);
        return true;
    }

    public static void clear() {
        if(!loaded)clearedAll=true;
        CACHE.clear();
        dirty(true);
        showActionBar("litematicainventories.cleared");
    }

    private static boolean dirty;
    private static long nextWrite;
    private static void dirty(boolean immediate) {
        dirty=true;
        if(!loaded) return;
        if(immediate) { PersistentInventoryStore.schedule(PersistentInventoryStore.encode(CACHE),true); dirty=false; }
        else if(nextWrite==0) nextWrite=System.currentTimeMillis()+750;
    }
    public static void tick() { loadReady(false);if(PersistentInventoryStore.takeWriteFailure()){dirty=true;nextWrite=System.currentTimeMillis()+1000;} if(dirty && loaded && System.currentTimeMillis()>=nextWrite) { nextWrite=0; dirty(true); } }
    public static void flush() { loadReady(false); if(dirty)dirty(true); }
    public static void shutdown() { loadReady(true); if(dirty||PersistentInventoryStore.takeWriteFailure())dirty(true); PersistentInventoryStore.awaitWrites(); }
    public static Map<CacheKey,ContainerSnapshot> snapshot() { loadReady(false); return Map.copyOf(CACHE); }
    public static ContainerSnapshot get(CacheKey key) {
        loadReady(false); ContainerSnapshot s=CACHE.get(key); var c=Minecraft.getInstance();
        if(s!=null && c.level!=null && ClientChunks.loaded(c.level,key.pos()) && key.server().equals(SessionContext.serverId(c)) && key.dimension().equals(SessionContext.dimensionId(c)) && !ContainerMenuAdapter.matchesSnapshotState(s.state(),c.level.getBlockState(key.pos()))) { remove(key); return null; }
        return s;
    }
    public static void remove(CacheKey key) { if(!loaded)removed.add(key); if(CACHE.remove(key)!=null || !loaded) dirty(false); }
    public static void clearWorld() { var c=Minecraft.getInstance(); String world=SessionContext.serverId(c); if(!loaded)clearedWorlds.add(world); CACHE.keySet().removeIf(k->k.server().equals(world)); dirty(true); }
    public static void clearContainer() { var o=ContainerRecorder.active(); if(o!=null) for(var p:o.adapter.halves()) remove(new CacheKey(o.server,o.dimension,p)); }
    public static int removeOlderThan(long hours) {
        if(hours<1||hours>876000)return 0;loadReady(true);long cutoff=System.currentTimeMillis()-hours*3600000L;
        var keys=CACHE.entrySet().stream().filter(e->e.getValue().observedAt()>0&&e.getValue().observedAt()<cutoff).map(Map.Entry::getKey).toList();
        keys.forEach(SessionInventoryCache::remove);flush();return keys.size();
    }

    public static int size() {
        loadReady(false); return CACHE.size();
    }

    public static InjectionResult injectInto(
            LitematicaSchematic schematic,
            Map<String, Box> subRegions,
            String server,
            String dimension
    ) {
        return injectInto(schematic,subRegions,server,dimension,snapshot());
    }
    public static InjectionResult injectInto(LitematicaSchematic schematic, Map<String,Box> subRegions, String server, String dimension, Map<CacheKey,ContainerSnapshot> source) {
        int candidates = 0;
        int injected = 0;

        for (Map.Entry<CacheKey, ContainerSnapshot> cacheEntry : source.entrySet()) {
            CacheKey key = cacheEntry.getKey();
            if (!key.server().equals(server) || !key.dimension().equals(dimension)) {
                continue;
            }

            for (Map.Entry<String, Box> regionEntry : subRegions.entrySet()) {
                Box box = regionEntry.getValue();
                if(box.getPos1()==null || box.getPos2()==null) continue;
                BlockPos min = PositionUtils.getMinCorner(box.getPos1(), box.getPos2());
                BlockPos max = PositionUtils.getMaxCorner(box.getPos1(), box.getPos2());
                if (!inside(key.pos(), min, max)) {
                    continue;
                }

                if(schematic.getSubRegionContainer(regionEntry.getKey())==null) continue;
                candidates++;
                BlockPos relativePos = key.pos().subtract(min);
                BlockState savedState = schematic.getSubRegionContainer(regionEntry.getKey())
                        .get(relativePos.getX(), relativePos.getY(), relativePos.getZ());
                ContainerSnapshot snapshot = cacheEntry.getValue();
                if (!savedState.hasBlockEntity() || !ContainerMenuAdapter.matchesSnapshotState(snapshot.state(),savedState)) {
                    continue;
                }

                Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion(regionEntry.getKey());
                CompoundData cachedData = DataConverterNbt.fromVanillaCompound(snapshot.blockEntityData().copy());
                if(blockEntities==null) continue;
                CompoundData existingData = blockEntities.get(relativePos);
                if(snapshot.stale() || snapshot.dataVersion()!=LitematicaSchematic.MINECRAFT_DATA_VERSION) continue;
                CompoundData data = existingData == null ? new CompoundData() : existingData.copy();
                if(existingData==null) data.putString("id",snapshot.blockEntityData().getStringOr("id",""));
                data.remove("LootTable"); data.remove("LootTableSeed");
                CompoundData observation=new CompoundData(); observation.putInt("Schema",1); observation.putInt("Size",snapshot.slots()); observation.putLong("Observed",snapshot.observedAt()); observation.putInt("DataVersion",snapshot.dataVersion()); observation.putBoolean("Complete",true);
                data.put(InventoryData.OBSERVATION,observation);
                cachedData.getData("Items").ifPresent(items -> data.put("Items", items.copy()));
                DataTypeUtils.writeBlockPosToTag(relativePos, data);
                blockEntities.put(relativePos, data);
                injected++;

            }
        }

        InjectionResult result = new InjectionResult(injected, candidates);
        showActionBar(Component.translatable("litematicainventories.injected", injected, candidates));
        return result;
    }

    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static void showActionBar(String translationKey) {
        showActionBar(Component.translatable(translationKey));
    }

    private static void showActionBar(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(message);
        }
    }

    public record InjectionResult(int injected, int candidates) {}
}
