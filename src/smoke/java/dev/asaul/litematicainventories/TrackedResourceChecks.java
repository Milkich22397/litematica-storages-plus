package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import fi.dy.masa.litematica.materials.*;
import java.nio.file.*;
import java.util.*;

/** Testmod only: fixtures are confined to the generated integrated test world. */
public final class TrackedResourceChecks {
    private static int checks;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static ContainerRecorder.Opening chest(Minecraft c,BlockPos pos,int count){
        var state=Blocks.CHEST.defaultBlockState();c.level.setBlock(pos,state,3);
        var menu=ChestMenu.threeRows(71,c.player.getInventory());fill(menu,count);
        var o=new ContainerRecorder.Opening(c.level,menu,ContainerMenuAdapter.create(menu,pos,state));o.synchronizedMenu=true;return o;
    }
    private static void fill(AbstractContainerMenu menu,int amount){for(int i=0;i<27;i++){int n=Math.min(64,amount);menu.slots.get(i).set(n==0?ItemStack.EMPTY:new ItemStack(Items.STONE,n));amount-=n;}}
    private static long count(Minecraft c,ContainerRecorder.Opening o){var e=TrackedStorageStore.snapshot().stream().filter(s->s.key().equals(TrackedStorageStore.key(o))).findFirst().orElseThrow();return InventoryData.decode(e.data(),e.size(),c.level.registryAccess()).stream().mapToLong(ItemStack::getCount).sum();}
    public static void run(Minecraft c,List<String> log)throws Exception{
        for(String name:List.of("fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry","fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList","fi.dy.masa.litematica.materials.MaterialListSorter"))Class.forName(name);
        Path proof=Path.of("resource-restart-proof.nbt");
        if(Files.exists(proof)){
            var saved=NbtIo.readCompressed(proof,NbtAccounter.create(16*1024*1024));var expected=TrackedStorageStore.decode(saved);
            check(!expected.isEmpty(),"restart witness is nonempty");
            for(var entry:expected.values())check(TrackedStorageStore.snapshot().stream().anyMatch(e->e.key().equals(entry.key())&&e.data().equals(entry.data())&&e.label().equals(entry.label())&&e.group().equals(entry.group())),"separate process loaded persisted resource snapshot");
            if(saved.contains("Organization"))check(StorageGroups.encode().equals(saved.getCompoundOrEmpty("Organization")),"groups, selection and schematic bindings persist across process restart");
            check(!StorageSettings.INSTANCE.trackingPositions.isEmpty(),"dragged position persisted across process restart");
            for(var raw:saved.getListOrEmpty("Buttons"))if(raw instanceof CompoundTag button){var position=StorageSettings.INSTANCE.trackingPositions.get(button.getStringOr("Layout",""));check(position!=null&&position.x()==button.getIntOr("X",0)&&position.y()==button.getIntOr("Y",0),"exact dragged coordinates restored after process restart");}
            log.add("RESOURCE RESTART PASS: previous process ledger, labels, groups, schematic bindings and button position loaded from disk");
        }
        var stone=new ItemStack(Items.STONE);var original=c.player.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList();
        boolean record=StorageSettings.INSTANCE.recording,mode=StorageSettings.INSTANCE.includeTrackedResources;
        boolean enderOption=fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.getBooleanValue();
        String profile=StorageSettings.INSTANCE.namespace;
        try{
            TrackedStorageStore.clear();StorageSettings.INSTANCE.resourceTracking=true;StorageSettings.INSTANCE.recording=false;StorageSettings.INSTANCE.includeTrackedResources=true;
            fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(false);
            c.player.getInventory().clearContent();c.player.getInventory().setItem(0,new ItemStack(Items.STONE,64));c.player.getInventory().setItem(1,new ItemStack(Items.STONE,64));
            var pos=c.player.blockPosition().offset(20,0,0);var a=chest(c,pos,320);
            check(ContainerMenuAdapter.create(c.player.inventoryMenu,pos,Blocks.CHEST.defaultBlockState())==null,"InventoryMenu rejected without getType");
            var unsupported=new AbstractContainerMenu(null,91){@Override public MenuType<?> getType(){throw new AssertionError("unsafe getType call");}@Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int i){return ItemStack.EMPTY;}@Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}};
            check(ContainerMenuAdapter.create(unsupported,pos,Blocks.CHEST.defaultBlockState())==null,"unregistered custom menu rejected before getType");
            for(var block:List.of(Blocks.FURNACE,Blocks.BLAST_FURNACE,Blocks.SMOKER,Blocks.HOPPER,Blocks.BREWING_STAND,Blocks.DISPENSER,Blocks.DROPPER)){
                var initial=block.defaultBlockState();
                for(var property:initial.getProperties())if(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty b&&Set.of("lit","enabled","triggered","has_bottle_0").contains(b.getName()))check(ContainerMenuAdapter.identityState(initial).equals(ContainerMenuAdapter.identityState(initial.setValue(b,!initial.getValue(b)))),"transient property does not replace "+block);
            }
            for(boolean on:List.of(false,true)){
                StorageSettings.INSTANCE.recording=on;
                net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(c.player,c.level,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));
                c.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player));check(ContainerRecorder.active()==null,"E/InventoryScreen after chest candidate is safe, recording="+on);c.setScreenAndShow(null);
            }
            StorageSettings.INSTANCE.recording=false;
            var barrel=Blocks.BARREL.defaultBlockState();var bp=pos.offset(6,0,0);c.level.setBlock(bp,barrel,3);
            var bk=new CacheKey(SessionContext.serverId(c),SessionContext.dimensionId(c),bp);
            var observed=barrel.setValue(BarrelBlock.OPEN,true);
            SessionInventoryCache.put(bk,new ContainerSnapshot("minecraft:barrel",observed.toString(),27,System.currentTimeMillis(),net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),InventoryData.encode(a.adapter.copyItems(),c.level.registryAccess())));
            check(SessionInventoryCache.get(bk)!=null,"closing barrel keeps schematic snapshot");
            check(ContainerMenuAdapter.matchesSnapshotState(observed.toString(),barrel)&&!ContainerMenuAdapter.matchesSnapshotState(observed.toString(),barrel.setValue(BarrelBlock.FACING,Direction.EAST)),"transient snapshot compatibility retains facing validation");
            TrackedStorageStore.toggle(a);check(TrackedStorageStore.tracked(a)&&TrackedStorageStore.snapshot().size()==1,"mark chest while schematic recording OFF");
            ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==448,"128 inventory + 320 tracked = 448");
            var list=new MaterialListCustom("resource test",Map.of(new fi.dy.masa.malilib.util.data.ItemType(stone,true,false),640),Path.of("resource-test.json"));
            list.setMaterialListEntries(List.of(new MaterialListEntry(stone,640,640,0,128)));
            var progress=ResourceAccounting.progress(list);check(progress.required()==640&&progress.collected()==448&&progress.remaining()==192&&progress.percent()==70,"640/448/192/70 percent example");
            var row=list.getMaterialsAll().getFirst();check(ResourceAccounting.displayed(row)==448&&row.getCountAvailable()==128,"combined display does not mutate native counts");
            StorageSettings.INSTANCE.includeTrackedResources=false;check(ResourceAccounting.displayed(row)==128,"inventory mode preserves native getter");StorageSettings.INSTANCE.includeTrackedResources=true;
            var b=chest(c,pos.offset(2,0,0),320);TrackedStorageStore.toggle(b);ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==768,"two independent chests sum");
            progress=ResourceAccounting.progress(list);check(progress.collected()==640&&progress.remaining()==0&&progress.percent()==100,"surplus clamps progress");
            TrackedStorageStore.toggle(b);ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==448,"untracking excludes immediately");
            fill(a.menu,64);TrackedStorageStore.observe(a,a.adapter.copyItems());ResourceAccounting.tick(c);check(count(c,a)==64&&ResourceAccounting.available(stone)==192,"tracked updates independently of recording");
            StorageSettings.INSTANCE.namespace="isolated-resource-test";ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==128,"world/server profile isolation");StorageSettings.INSTANCE.namespace=profile;ResourceAccounting.tick(c);
            SessionInventoryCache.clear();check(TrackedStorageStore.tracked(a),"schematic cache clear keeps ledger");
            TrackedStorageStore.awaitWrites();var disk=NbtIo.readCompressed(StorageSettings.DIRECTORY.resolve("tracked-storages.nbt"),NbtAccounter.create(16*1024*1024));check(TrackedStorageStore.decode(disk).values().iterator().next().data().equals(TrackedStorageStore.snapshot().getFirst().data()),"atomic resource file round trip");
            c.level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);TrackedStorageStore.validateLoaded(c);ResourceAccounting.tick(c);check(ResourceAccounting.stored(stone)==0,"known removed/replaced block excluded");
            TrackedStorageStore.clear();
            var right=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,ChestType.RIGHT);BlockPos other=ChestBlock.getConnectedBlockPos(pos,right);
            c.level.setBlock(pos,right,3);c.level.setBlock(other,right.setValue(ChestBlock.TYPE,ChestType.LEFT),3);
            var menu=ChestMenu.sixRows(72,c.player.getInventory());fill(menu,320);var d=new ContainerRecorder.Opening(c.level,menu,ContainerMenuAdapter.create(menu,pos,right));d.synchronizedMenu=true;
            var fromOther=new ContainerRecorder.Opening(c.level,menu,ContainerMenuAdapter.create(menu,other,c.level.getBlockState(other)));fromOther.synchronizedMenu=true;
            TrackedStorageStore.toggle(d);TrackedStorageStore.observe(fromOther,fromOther.adapter.copyItems());ResourceAccounting.tick(c);check(TrackedStorageStore.snapshot().size()==1&&ResourceAccounting.stored(stone)==320&&TrackedStorageStore.tracked(fromOther),"double chest canonical key, both halves, no double count");
            check(StorageLocator.show(TrackedStorageStore.get(d.trackingKey)),"double chest can be highlighted");StorageLocator.opened(fromOther);check(StorageLocator.target()==null,"opening either double-chest half dismisses its highlight");StorageLocator.reset();
            c.level.setBlock(other,Blocks.AIR.defaultBlockState(),3);TrackedStorageStore.validateLoaded(c);ResourceAccounting.tick(c);check(ResourceAccounting.stored(stone)==0,"breaking one double chest half invalidates entire snapshot");
            TrackedStorageStore.clear();
            var endPos=pos.offset(4,0,0);c.level.setBlock(endPos,Blocks.ENDER_CHEST.defaultBlockState(),3);var em=ChestMenu.threeRows(73,c.player.getInventory());fill(em,64);var end=new ContainerRecorder.Opening(c.level,em,ContainerMenuAdapter.create(em,endPos,Blocks.ENDER_CHEST.defaultBlockState()));end.synchronizedMenu=true;
            TrackedStorageStore.toggle(end);var ep=endPos.offset(3,0,0);c.level.setBlock(ep,Blocks.ENDER_CHEST.defaultBlockState(),3);var end2=new ContainerRecorder.Opening(c.level,em,ContainerMenuAdapter.create(em,ep,Blocks.ENDER_CHEST.defaultBlockState()));end2.synchronizedMenu=true;
            long endRevision=TrackedStorageStore.revision();TrackedStorageStore.observe(end2,end2.adapter.copyItems());check(TrackedStorageStore.revision()==endRevision,"unchanged ender observation does not rewrite ledger");ResourceAccounting.tick(c);check(TrackedStorageStore.snapshot().size()==1&&TrackedStorageStore.tracked(end2)&&ResourceAccounting.stored(stone)==64,"ender chest identity is player/server, not block/dimension");
            var tracker=fi.dy.masa.malilib.registry.Registry.ENTITY_DATA_REGISTRY.chestTracker();var nativeCache=tracker.getClass().getDeclaredField("enderCache");nativeCache.setAccessible(true);var oldCache=nativeCache.get(tracker);
            try{tracker.onCloseEnderChest(new net.minecraft.world.SimpleContainer(new ItemStack(Items.STONE,64)));fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(true);ResourceAccounting.tick(c);check(ResourceAccounting.available(stone)==192,"tracked ender and native Chest Tracker ender cache counted only once");}
            finally{nativeCache.set(tracker,oldCache);fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(false);}
            check(TrackedStorageStore.snapshot().getFirst().positions().isEmpty(),"ender snapshot has no physical position");
            var detached=TrackedStorageStore.snapshot().getFirst().data();detached.remove("Items");check(TrackedStorageStore.snapshot().getFirst().data().contains("Items"),"resource NBT defensive accessor");
            TrackedStorageStore.toggle(end2);check(TrackedStorageStore.snapshot().isEmpty(),"untrack ender from any access point");
            var before=SessionInventoryCache.size();TrackedStorageStore.clear();check(SessionInventoryCache.size()==before,"clearing tracked ledger never clears schematic cache");
            TrackedStorageStore.awaitWrites();check(TrackedStorageStore.decode(NbtIo.readCompressed(StorageSettings.DIRECTORY.resolve("tracked-storages.nbt.bak"),NbtAccounter.create(16*1024*1024))).isEmpty(),"clear mirrored to backup, no resurrection");
            log.add("RESOURCE PASS: "+checks+" assertions; E/crash, 640/448/192/70%, recording OFF, modes, immutable native counts, profiles, files, double and ender identities, invalidation and independent clears");
        }finally{
            TrackedStorageStore.clear();StorageSettings.INSTANCE.recording=record;StorageSettings.INSTANCE.includeTrackedResources=mode;StorageSettings.INSTANCE.namespace=profile;
            fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(enderOption);
            c.player.getInventory().clearContent();for(int n=0;n<original.size();n++)c.player.getInventory().setItem(n,original.get(n));ResourceAccounting.tick(c);
        }
    }
    public static void persistRestartProof()throws Exception{
        TrackedStorageStore.awaitWrites();var map=new LinkedHashMap<TrackedStorageStore.Key,TrackedStorageStore.Entry>();for(var e:TrackedStorageStore.snapshot())map.put(e.key(),e);
        if(map.isEmpty())throw new AssertionError("No tracked inventory for restart proof");var root=TrackedStorageStore.encode(map);var buttons=new ListTag();StorageSettings.INSTANCE.trackingPositions.forEach((layout,p)->{var tag=new CompoundTag();tag.putString("Layout",layout);tag.putInt("X",p.x());tag.putInt("Y",p.y());buttons.add(tag);});root.put("Buttons",buttons);NbtIo.writeCompressed(root,Path.of("resource-restart-proof.nbt"));
    }
}
