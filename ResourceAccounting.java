package dev.asaul.litematicainventories;
import fi.dy.masa.litematica.materials.*;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.malilib.util.data.ItemType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Display-only resource totals. Never changes MaterialListEntry or placement/Easy Place state. */
public final class ResourceAccounting {
    private static final Map<ItemType,Long> tracked=new HashMap<>(),inventory=new HashMap<>();
    private record Counted(TrackedStorageStore.Entry entry,it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<ItemType> counts){}
    private static final Map<TrackedStorageStore.Key,Counted> decoded=new HashMap<>();
    private static long generation;
    public static long generation(){return generation;}
    private static long ledgerRevision=-1;
    private static String context="";
    private static Object registries;
    private static boolean ownEnder;
    public static boolean combined(){return StorageSettings.INSTANCE.resourceTracking&&StorageSettings.INSTANCE.includeTrackedResources;}
    public static void tick(Minecraft c){
        var previousInventory=new HashMap<>(inventory);inventory.clear();if(c.level==null||c.player==null){if(!tracked.isEmpty()||!previousInventory.isEmpty())generation++;tracked.clear();decoded.clear();ledgerRevision=-1;return;}
        String key=SessionContext.serverId(c)+"|"+c.player.getUUID();
        long revision=TrackedStorageStore.revision();
        if(revision!=ledgerRevision||!context.equals(key)||registries!=c.level.registryAccess()){
            if(!context.equals(key)||registries!=c.level.registryAccess())decoded.clear();
            context=key;registries=c.level.registryAccess();tracked.clear();ownEnder=false;var retained=new HashSet<TrackedStorageStore.Key>();
            for(var e:TrackedStorageStore.snapshot())if(TrackedStorageStore.current(e,c)){
                try{
                    retained.add(e.key());var old=decoded.get(e.key());
                    if(old==null||old.entry()!=e){var items=InventoryData.decode(e.data(),e.size(),c.level.registryAccess());old=new Counted(e,MaterialListUtils.getInventoryItemCounts(new SimpleContainer(items.toArray(ItemStack[]::new))));decoded.put(e.key(),old);}
                    add(tracked,old.counts());
                    ownEnder|=e.personal();
                }catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger("StoragesPlus").error("Cannot count tracked inventory {} (excluded)",e.key(),ex);}
            }
            decoded.keySet().retainAll(retained);ledgerRevision=revision;generation++;
        }
        add(inventory,MaterialListUtils.getInventoryItemCounts(c.player.getInventory()));
        // Litematica may already count Chest Tracker's ender cache. Use exactly one source.
        if((!ownEnder||!combined())&&Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.getBooleanValue()){
            var tracker=fi.dy.masa.malilib.registry.Registry.ENTITY_DATA_REGISTRY.chestTracker();
            var cache=tracker==null?null:tracker.getEnderCache();
            var container=cache==null?null:cache.toInventory(27);
            if(container!=null)add(inventory,MaterialListUtils.getInventoryItemCounts(container));
        }
        if(!inventory.equals(previousInventory))generation++;
    }
    private static void add(Map<ItemType,Long> to,it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<ItemType> values){values.object2IntEntrySet().forEach(e->to.merge(e.getKey(),(long)e.getIntValue(),Long::sum));}
    public static long stored(ItemStack stack){return tracked.getOrDefault(new ItemType(stack,true,false),0L);}
    public static long available(ItemStack stack){var key=new ItemType(stack,true,false);return inventory.getOrDefault(key,0L)+(combined()?tracked.getOrDefault(key,0L):0);}
    public static int displayed(MaterialListEntry entry){return combined()?(int)Math.min(Integer.MAX_VALUE,available(entry.getStack())):entry.getCountAvailable();}
    public static long required(MaterialListEntry entry,MaterialListBase list){return list.getMultiplier()==1?Math.max(0,entry.getCountMissing()):(long)entry.getCountTotal()*Math.max(1,list.getMultiplier());}
    public static int remaining(MaterialListEntry entry,MaterialListBase list){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,required(entry,list)-available(entry.getStack())));}
    public static List<MaterialListEntry> hudEntries(MaterialListBase list){
        var ignored=((dev.asaul.litematicainventories.mixin.ResourceMaterialListAccess)list).storages$ignored();
        var rows=new ArrayList<MaterialListEntry>();
        for(var row:list.getMaterialsAll())if(!ignored.contains(row)&&remaining(row,list)>0)rows.add(row);
        rows.sort(new MaterialListSorter(list));
        return rows;
    }
    public record Progress(long required,long collected,long remaining,double percent){}
    public static Progress progress(MaterialListBase list){
        long required=0,collected=0;int multiplier=Math.max(1,list.getMultiplier());
        for(var row:list.getMaterialsAll()){
            if(((dev.asaul.litematicainventories.mixin.ResourceMaterialListAccess)list).storages$ignored().contains(row))continue;
            long need=multiplier==1?Math.max(0,row.getCountMissing()):(long)row.getCountTotal()*multiplier;
            required+=need;collected+=Math.min(need,Math.max(0,available(row.getStack())));
        }
        return new Progress(required,collected,Math.max(0,required-collected),required==0?100:100.0*collected/required);
    }
    private ResourceAccounting(){}
}
