package dev.asaul.litematicainventories;

import fi.dy.masa.litematica.materials.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;

public final class OptimizationChecks {
    private static int checks;
    private static volatile long sink;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void run(Minecraft c,List<String> log)throws Exception{
        var inventory=c.player.getInventory();var original=inventory.getNonEquipmentItems().stream().map(ItemStack::copy).toList();
        boolean mode=StorageSettings.INSTANCE.includeTrackedResources;
        boolean ender=fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.getBooleanValue();
        try{
            StorageSettings.INSTANCE.includeTrackedResources=true;
            fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(false);
            inventory.clearContent();var stone=new ItemStack(Items.STONE);
            var box=new ItemStack(Items.SHULKER_BOX);box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE,64))));inventory.setItem(0,box);
            ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==64,"nested shulker items counted");long generation=ResourceAccounting.generation();
            for(int n=0;n<1000;n++)ResourceAccounting.tick(c);
            check(ResourceAccounting.generation()==generation,"unchanged inventory does not invalidate display caches");
            box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE,32))));ResourceAccounting.tick(c);
            check(ResourceAccounting.available(stone)==32&&ResourceAccounting.generation()!=generation,"changed nested components invalidate inventory cache");
            box.setCount(2);ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==64,"changed stack count invalidates cache");

            var list=new MaterialListCustom("cache checks",Map.of(),Path.of("optimization-test.json"));
            var row=new MaterialListEntry(stone,640,640,0,0);list.setMaterialListEntries(List.of(row));
            var rows=ResourceAccounting.hudEntries(list);var progress=ResourceAccounting.progress(list);
            for(int n=0;n<1000;n++)check(ResourceAccounting.hudEntries(list)==rows&&ResourceAccounting.progress(list)==progress,"stable HUD and progress reuse prepared objects");
            list.setMultiplier(2);check(ResourceAccounting.progress(list).remaining()==1216&&ResourceAccounting.hudEntries(list)!=rows,"multiplier invalidates prepared display");
            list.ignoreEntry(row);check(ResourceAccounting.hudEntries(list).isEmpty()&&ResourceAccounting.progress(list).required()==0,"ignore invalidates prepared display");
            list.clearIgnored();check(ResourceAccounting.hudEntries(list).size()==1,"clear ignored invalidates prepared display");
            rows=ResourceAccounting.hudEntries(list);list.setSortCriteria(MaterialListBase.SortCriteria.NAME);check(ResourceAccounting.hudEntries(list)!=rows,"sorting invalidates prepared display");
            list.setMultiplier(1);list.setMaterialListEntries(List.of(new MaterialListEntry(stone,32,32,0,0)));check(ResourceAccounting.hudEntries(list).isEmpty(),"replacement entries invalidate prepared display");
            var entries=new ArrayList<MaterialListEntry>();for(int n=0;n<1000;n++)entries.add(new MaterialListEntry(stone,1000+(n*37)%997,1000+(n*37)%997,0,0));list.setMaterialListEntries(entries);
            ResourceAccounting.hudEntries(list);ResourceAccounting.progress(list);
            for(int n=0;n<20;n++){oldHud(list);ResourceAccounting.hudEntries(list);}
            long start=System.nanoTime();for(int n=0;n<500;n++)sink+=oldHud(list).size();long oldNs=System.nanoTime()-start;
            start=System.nanoTime();for(int n=0;n<500;n++){sink+=ResourceAccounting.hudEntries(list).size();sink+=ResourceAccounting.progress(list).remaining();}long cachedNs=System.nanoTime()-start;
            log.add(String.format(Locale.ROOT,"PERF SAMPLE (not an FPS benchmark): 1000 material rows, 500 calls; former HUD scan/sort %.3f ms, cached HUD+progress %.3f ms",oldNs/1e6,cachedNs/1e6));

            var region=new fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer(32,32,32);
            check(!ContainerMenuAdapter.hasStoragePalette(region),"air-only region skips volume scan");
            region.set(1,1,1,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());check(!ContainerMenuAdapter.hasStoragePalette(region),"building-only region skips volume scan");
            region.set(2,2,2,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());check(ContainerMenuAdapter.hasStoragePalette(region),"container palette preserves unknown and known storage scan");

            SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();
            var tagged=PersistentInventoryStore.encode(SessionInventoryCache.snapshot());tagged.putString("OptimizationProbe","pending-write-completed");
            PersistentInventoryStore.schedule(tagged,false);PersistentInventoryStore.awaitWrites();
            var file=StorageSettings.DIRECTORY.resolve("containers.nbt");
            check(NbtIo.readCompressed(file,NbtAccounter.create(16*1024*1024)).getStringOr("OptimizationProbe","").equals("pending-write-completed"),"awaitWrites waits for delayed scheduled save");
            SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();
            check(NbtIo.readCompressed(file,NbtAccounter.create(16*1024*1024)).contains("OptimizationProbe"),"unchanged cache flush does not rewrite whole file");
            // Deliberately block the temp file only inside run-smoke, then allow the normal retry.
            var blocked=StorageSettings.DIRECTORY.resolve("containers.nbt.tmp");Files.createDirectory(blocked);
            var retryKey=new CacheKey("optimization-retry","test:dim",net.minecraft.core.BlockPos.ZERO);
            var retryData=InventoryData.encode(Collections.nCopies(27,ItemStack.EMPTY),c.level.registryAccess());
            try{
                SessionInventoryCache.put(retryKey,new ContainerSnapshot("minecraft:chest",net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState().toString(),27,System.currentTimeMillis(),net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),retryData));
                SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();
                check(!PersistentInventoryStore.error.isBlank(),"failed save is reported");
                SessionInventoryCache.tick();
            }finally{Files.delete(blocked);}
            Thread.sleep(1100);SessionInventoryCache.tick();PersistentInventoryStore.awaitWrites();
            check(PersistentInventoryStore.load().containsKey(retryKey)&&PersistentInventoryStore.error.isBlank(),"failed cache save retries and recovers");
            SessionInventoryCache.remove(retryKey);SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();
            cacheBounds(c);
            log.add("OPTIMIZATION PASS: "+checks+" checks; nested items, cached HUD/progress invalidation, unchanged inventory, delayed writes, no redundant flush");
        }finally{
            inventory.clearContent();for(int n=0;n<original.size();n++)inventory.setItem(n,original.get(n));StorageSettings.INSTANCE.includeTrackedResources=mode;
            fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(ender);ResourceAccounting.tick(c);
        }
    }
    @SuppressWarnings("unchecked")
    private static void cacheBounds(Minecraft c)throws Exception{
        var cacheField=SessionInventoryCache.class.getDeclaredField("CACHE");cacheField.setAccessible(true);
        var cache=(Map<CacheKey,ContainerSnapshot>)cacheField.get(null);var saved=new HashMap<>(cache);int limit=StorageSettings.INSTANCE.maxEntries;
        try{
            var loading=SessionInventoryCache.class.getDeclaredField("LOADING");loading.setAccessible(true);
            check(loading.get(null)==null,"completed async load releases its source map");
            var removed=SessionInventoryCache.class.getDeclaredField("removed");removed.setAccessible(true);
            for(int n=0;n<1000;n++)SessionInventoryCache.remove(new CacheKey("missing-audit","test:dim",new net.minecraft.core.BlockPos(n,0,0)));
            check(((Set<?>)removed.get(null)).isEmpty(),"post-load removals do not retain tombstones indefinitely");
            cache.clear();var tag=InventoryData.encode(Collections.nCopies(27,ItemStack.EMPTY),c.level.registryAccess());
            var keys=new ArrayList<CacheKey>();
            for(int n=0;n<2000;n++){var key=new CacheKey("audit-limit","test:dim",new net.minecraft.core.BlockPos(n,0,0));keys.add(key);cache.put(key,new ContainerSnapshot("minecraft:chest",net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState().toString(),27,n,net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),tag));}
            StorageSettings.INSTANCE.maxEntries=3;
            var newest=new CacheKey("audit-limit","test:dim",new net.minecraft.core.BlockPos(2000,0,0));
            SessionInventoryCache.put(newest,new ContainerSnapshot("minecraft:chest",net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState().toString(),27,2000,net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),tag));
            check(cache.size()==3&&cache.containsKey(keys.get(1998))&&cache.containsKey(keys.get(1999))&&cache.containsKey(newest),"large limit reduction retains only newest observations in one batch");
        }finally{StorageSettings.INSTANCE.maxEntries=limit;cache.clear();cache.putAll(saved);var dirty=SessionInventoryCache.class.getDeclaredMethod("dirty",boolean.class);dirty.setAccessible(true);dirty.invoke(null,true);PersistentInventoryStore.awaitWrites();}
    }
    private static List<MaterialListEntry> oldHud(MaterialListBase list){
        var ignored=((dev.asaul.litematicainventories.mixin.ResourceMaterialListAccess)list).storages$ignored();var rows=new ArrayList<MaterialListEntry>();
        for(var row:list.getMaterialsAll())if(!ignored.contains(row)&&ResourceAccounting.remaining(row,list)>0)rows.add(row);
        rows.sort(new MaterialListSorter(list));return rows;
    }
}
