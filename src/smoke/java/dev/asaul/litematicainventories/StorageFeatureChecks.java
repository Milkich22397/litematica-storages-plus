package dev.asaul.litematicainventories;

import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.*;
import fi.dy.masa.litematica.materials.*;
import fi.dy.masa.litematica.schematic.*;
import fi.dy.masa.litematica.selection.*;
import java.nio.file.*;
import java.util.*;

public final class StorageFeatureChecks {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static ContainerRecorder.Opening fixture(Minecraft c,BlockPos pos,int count,boolean ender){
        var state=(ender?Blocks.ENDER_CHEST:Blocks.CHEST).defaultBlockState();c.level.setBlock(pos,state,3);
        var menu=ChestMenu.threeRows(88,c.player.getInventory());for(int n=0;n<27;n++){int amount=Math.min(count,64);menu.slots.get(n).set(amount==0?ItemStack.EMPTY:new ItemStack(Items.STONE,amount));count-=amount;}
        var opening=new ContainerRecorder.Opening(c.level,menu,ContainerMenuAdapter.create(menu,pos,state));opening.synchronizedMenu=true;TrackedStorageStore.toggle(opening);return opening;
    }
    private static MaterialListSchematic list(String file)throws Exception{
        var area=new AreaSelection();area.setExplicitOrigin(BlockPos.ZERO);area.addSubRegionBox(new Box(BlockPos.ZERO,BlockPos.ZERO,"group"),false);
        var schematic=LitematicaSchematic.createEmptySchematic(area,file);Path dir=Path.of("test-schematics");Files.createDirectories(dir);check(schematic.writeToFile(dir,file,true),"write binding fixture");
        var loaded=LitematicaSchematic.createFromFile(dir,file+".litematic");var result=new MaterialListSchematic(loaded,false);result.setMaterialListEntries(List.of(new MaterialListEntry(new ItemStack(Items.STONE),640,640,0,16)));return result;
    }
    public static void run(Minecraft c,List<String> log)throws Exception{
        var savedOrganization=StorageGroups.encode();var original=c.player.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList();
        boolean mode=StorageSettings.INSTANCE.includeTrackedResources,enabled=StorageSettings.INSTANCE.resourceTracking;
        boolean nativeEnder=fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.getBooleanValue();
        try{
            TrackedStorageStore.clear();StorageSettings.INSTANCE.includeTrackedResources=true;StorageSettings.INSTANCE.resourceTracking=true;
            fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(false);
            c.player.getInventory().clearContent();c.player.getInventory().setItem(0,new ItemStack(Items.STONE,16));
            String server=StorageGroups.server(),a=StorageGroups.create(server,"Build A"),b=StorageGroups.create(server,"Build B");
            check(a!=null&&b!=null&&!a.equals(b),"create separate persistent groups");check(StorageGroups.create(server,"build a").equals(a),"duplicate group names reuse existing group");
            var pos=c.player.blockPosition().offset(4,0,5);var one=fixture(c,pos,320,false);var two=fixture(c,pos.offset(2,0,0),64,false);var ender=fixture(c,pos.offset(4,0,0),128,true);
            TrackedStorageStore.edit(one.trackingKey,"Stone stock",a);TrackedStorageStore.edit(two.trackingKey,"Small chest",b);TrackedStorageStore.edit(ender.trackingKey,"Personal",b);
            StorageGroups.select(server,StorageGroups.ALL);ResourceAccounting.tick(c);var stone=new ItemStack(Items.STONE);check(ResourceAccounting.available(stone)==528,"all groups count inventory plus three storages");
            StorageGroups.select(server,a);check(ResourceAccounting.available(stone)==336,"group A excludes other storages including personal ender");
            var da=ResourceAccounting.breakdown(stone,null);check(da.inventory()==16&&da.ender()==0&&da.storages()==320&&da.total()==336,"breakdown matches selected source counts");
            StorageGroups.select(server,b);var db=ResourceAccounting.breakdown(stone,null);check(db.inventory()==16&&db.ender()==128&&db.storages()==64&&db.total()==208,"ender source counted exactly once in selected group");
            StorageGroups.select(server,"");check(ResourceAccounting.available(stone)==16,"ungrouped selection excludes assigned storages");
            var first=list("binding-a");var second=list("binding-b");StorageGroups.bind(server,first,a);StorageGroups.bind(server,second,b);
            check(ResourceAccounting.available(stone,first)==336&&ResourceAccounting.available(stone,second)==208,"two schematic bindings keep independent counts");
            check(ResourceAccounting.remaining(first.getMaterialsAll().getFirst(),first)==304&&ResourceAccounting.remaining(second.getMaterialsAll().getFirst(),second)==432,"per-schematic remaining values are independent");
            check(ResourceAccounting.progress(first).collected()==336&&ResourceAccounting.progress(second).collected()==208,"HUD and progress respect each schematic group");
            var reloaded=list("binding-a");check(StorageGroups.selection(server,reloaded).equals(a),"binding uses schematic file rather than runtime list identity");
            check(StorageGroups.selection(server+"-other",first).equals(StorageGroups.ALL),"group bindings isolated by world/profile");
            TrackedStorageStore.observe(one,one.adapter.copyItems());check(TrackedStorageStore.get(one.trackingKey).label().equals("Stone stock")&&TrackedStorageStore.get(one.trackingKey).group().equals(a),"observation retains edited metadata");
            var encoded=TrackedStorageStore.encode(new LinkedHashMap<>(Map.of(one.trackingKey,TrackedStorageStore.get(one.trackingKey))));var decoded=TrackedStorageStore.decode(encoded).get(one.trackingKey);
            check(decoded.label().equals("Stone stock")&&decoded.group().equals(a),"labels and membership round trip");
            StorageGroups.restore(encoded.getCompoundOrEmpty("Organization"));check(StorageGroups.selection(server,first).equals(a),"group definitions and bindings round trip");
            TrackedStorageStore.awaitWrites();var disk=NbtIo.readCompressed(StorageSettings.DIRECTORY.resolve("tracked-storages.nbt"),NbtAccounter.create(32*1024*1024));check(disk.getCompoundOrEmpty("Organization").getListOrEmpty("Groups").size()>=2,"metadata written in atomic ledger");
            check(StorageLocator.show(TrackedStorageStore.get(one.trackingKey))&&StorageLocator.target().equals(one.trackingKey),"physical storage can be targeted for highlight");
            var shownField=StorageLocator.class.getDeclaredField("shownAt");shownField.setAccessible(true);var expiresField=StorageLocator.class.getDeclaredField("expires");expiresField.setAccessible(true);
            long shown=shownField.getLong(null),expires=expiresField.getLong(null);check(expires-shown==15_000_000_000L,"locator lifetime is fifteen seconds including fade-out");
            check(StorageLocator.opacity(shown)==0&&StorageLocator.opacity(shown+250_000_000L)==1&&StorageLocator.opacity(expires)==0,"opacity starts invisible, reaches full visibility, and is zero at timeout");
            float last=0;for(long elapsed=0;elapsed<=250_000_000L;elapsed+=25_000_000L){float alpha=StorageLocator.opacity(shown+elapsed);check(alpha>=last&&alpha>=0&&alpha<=1,"fade-in is bounded and monotonic");last=alpha;}
            last=1;for(long left=350_000_000L;left>=0;left-=50_000_000L){float alpha=StorageLocator.opacity(expires-left);check(alpha<=last&&alpha>=0,"timeout fades continuously to zero");last=alpha;}
            StorageLocator.opened(two);check(StorageLocator.target().equals(one.trackingKey),"opening a different container keeps the target");
            shownField.setLong(null,System.nanoTime()-500_000_000L);StorageLocator.opened(one);check(StorageLocator.target()==null,"opening the targeted container deselects it");
            var fadeField=StorageLocator.class.getDeclaredField("fadeStarted");fadeField.setAccessible(true);long fading=fadeField.getLong(null);
            check(StorageLocator.opacity(fading)==1,"opening begins fade from current opacity without a brightness jump");
            last=1;for(long elapsed=0;elapsed<=350_000_000L;elapsed+=50_000_000L){float alpha=StorageLocator.opacity(fading+elapsed);check(alpha<=last&&alpha>=0,"opening fade-out is bounded and monotonic");last=alpha;}
            check(StorageLocator.opacity(fading+350_000_000L)==0,"opening completes fade-out in 350 ms");
            check(StorageLocator.show(TrackedStorageStore.get(one.trackingKey))&&StorageLocator.target().equals(one.trackingKey),"new locate cancels an old fading target");
            check(!StorageLocator.show(TrackedStorageStore.get(ender.trackingKey)),"personal ender has no invented physical location");StorageLocator.reset();check(StorageLocator.target()==null&&StorageLocator.opacity(System.nanoTime())==0,"disconnect reset removes the outline immediately");
            StorageGroups.rename(server,a,"Renamed");check(StorageGroups.name(server,a).equals("Renamed")&&ResourceAccounting.available(stone,first)==336,"rename retains stable membership");
            StorageGroups.delete(server,a);check(TrackedStorageStore.get(one.trackingKey).group().isEmpty()&&!StorageGroups.bound(server,first),"delete group safely ungroups entries and removes binding");
            check(!new StorageSettings().resourceBreakdown,"resource breakdown defaults off");
            var wanted=new ItemStack(Items.SHULKER_BOX);var actual=new ItemStack(Items.SHULKER_BOX);
            wanted.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE,64),new ItemStack(Items.DIAMOND,5))));actual.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE,32),new ItemStack(Items.DIRT,5))));
            var diff=NestedContents.compare(wanted,actual);check(diff.differences().size()==2,"nested comparison detects count and item differences");check(diff.tooltip(false).size()==2&&diff.tooltip(true).size()==3,"nested details stay compact until expanded");
            check(NestedContents.compare(wanted,wanted.copy()).differences().isEmpty(),"equal nested inventories produce no extra text");
            var outerA=new ItemStack(Items.SHULKER_BOX);var outerB=new ItemStack(Items.SHULKER_BOX);outerA.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(wanted)));outerB.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(actual)));check(NestedContents.compare(outerA,outerB).differences().getFirst().slot().equals("1 › 1"),"nested paths include container hierarchy");
            var many=new ArrayList<ItemStack>(Collections.nCopies(27,new ItemStack(Items.STONE)));wanted.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(many));actual.remove(DataComponents.CONTAINER);var pages=NestedContents.compare(wanted,actual);check(pages.differences().size()==27&&pages.tooltip(true,3).get(1).getString().contains("25"),"all nested differences accessible on paged details");
            log.add("STORAGE FEATURES PASS: "+checks+" assertions; groups, per-file bindings, source breakdown, persistent labels, safe delete, fifteen-second locator lifetime, smooth fades, matching-container dismissal and nested slot comparisons");
        }finally{
            StorageLocator.reset();TrackedStorageStore.clear();StorageGroups.restore(savedOrganization);TrackedStorageStore.organizationChanged();
            c.player.getInventory().clearContent();for(int n=0;n<original.size();n++)c.player.getInventory().setItem(n,original.get(n));StorageSettings.INSTANCE.includeTrackedResources=mode;StorageSettings.INSTANCE.resourceTracking=enabled;
            fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.setBooleanValue(nativeEnder);ResourceAccounting.tick(c);
        }
    }
}
