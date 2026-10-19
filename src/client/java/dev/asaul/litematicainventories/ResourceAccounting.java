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
    private static final Map<ItemType,Long> tracked=new HashMap<>();
    private static Map<ItemType,Long> inventory=Map.of(),playerItems=Map.of(),nativeEnderItems=Map.of();
    private static final Map<String,Map<ItemType,Long>> grouped=new HashMap<>(),personalGroups=new HashMap<>();
    private static final Map<ItemType,Long> personalItems=new HashMap<>();
    private static Object inventoryOwner,lastEnderCache;
    private static List<ItemStack> inventorySnapshot=List.of();
    private static boolean lastUseEnder,lastCombined;
    private record View(long generation,long revision,String selection,List<MaterialListEntry> source,List<MaterialListEntry> hud,Progress progress){}
    // Values deliberately do not hold the list/key or a MaterialListSorter referencing it.
    private static final Map<MaterialListBase,View> views=new WeakHashMap<>();
    private record Counted(TrackedStorageStore.Entry entry,it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<ItemType> counts){}
    private static final Map<TrackedStorageStore.Key,Counted> decoded=new HashMap<>();
    private static long generation;
    public static long generation(){return generation;}
    private static long ledgerRevision=-1;
    private static String context="",currentServer="";
    private static Object registries;
    private static boolean ownEnder;
    public static boolean combined(){return StorageSettings.INSTANCE.resourceTracking&&StorageSettings.INSTANCE.includeTrackedResources;}
    public static void tick(Minecraft c){
        boolean mode=combined();if(mode!=lastCombined){lastCombined=mode;generation++;}
        if(c.level==null||c.player==null){
            if(!tracked.isEmpty()||!inventory.isEmpty())generation++;
            tracked.clear();grouped.clear();personalGroups.clear();personalItems.clear();playerItems=Map.of();nativeEnderItems=Map.of();inventory=Map.of();decoded.clear();views.clear();ledgerRevision=-1;inventoryOwner=null;inventorySnapshot=List.of();lastEnderCache=null;registries=null;context="";currentServer="";ownEnder=false;return;
        }
        currentServer=SessionContext.serverId(c);String key=currentServer+"|"+c.player.getUUID();
        long revision=TrackedStorageStore.revision();
        if(revision!=ledgerRevision||!context.equals(key)||registries!=c.level.registryAccess()){
            if(!context.equals(key)||registries!=c.level.registryAccess())decoded.clear();
            context=key;registries=c.level.registryAccess();tracked.clear();grouped.clear();personalGroups.clear();personalItems.clear();ownEnder=false;var retained=new HashSet<TrackedStorageStore.Key>();
            String server=currentServer;String player=c.player.getUUID().toString();
            for(var e:TrackedStorageStore.snapshot())if(TrackedStorageStore.current(e,server,player)){
                try{
                    retained.add(e.key());var old=decoded.get(e.key());
                    if(old==null||old.entry()!=e){var items=InventoryData.decode(e.data(),e.size(),c.level.registryAccess());old=new Counted(e,MaterialListUtils.getInventoryItemCounts(new SimpleContainer(items.toArray(ItemStack[]::new))));decoded.put(e.key(),old);}
                    add(tracked,old.counts());add(grouped.computeIfAbsent(e.group(),g->new HashMap<>()),old.counts());
                    if(e.personal()){add(personalItems,old.counts());add(personalGroups.computeIfAbsent(e.group(),g->new HashMap<>()),old.counts());}
                    ownEnder|=e.personal();
                }catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger("StoragesPlus").error("Cannot count tracked inventory {} (excluded)",e.key(),ex);}
            }
            decoded.keySet().retainAll(retained);ledgerRevision=revision;generation++;
        }
        // Comparing 41 client slots is cheap; unpacking shulker/bundle contents every tick is not.
        var source=c.player.getInventory();
        var tracker=fi.dy.masa.malilib.registry.Registry.ENTITY_DATA_REGISTRY.chestTracker();
        var ender=tracker==null?null:tracker.getEnderCache();
        boolean useEnder=(!ownEnder||!mode)&&Configs.Generic.MATERIAL_LIST_COUNT_ENDER_CACHE.getBooleanValue();
        boolean changed=inventoryOwner!=source||inventorySnapshot.size()!=source.getContainerSize();
        if(!changed)for(int n=0;n<inventorySnapshot.size();n++)if(!ItemStack.matches(inventorySnapshot.get(n),source.getItem(n))){changed=true;break;}
        if(changed||lastEnderCache!=ender||lastUseEnder!=useEnder){
            var playerCounts=new HashMap<ItemType,Long>();add(playerCounts,MaterialListUtils.getInventoryItemCounts(source));playerItems=playerCounts;
            var next=new HashMap<ItemType,Long>(playerCounts);var enderCounts=new HashMap<ItemType,Long>();
            if(useEnder&&ender!=null){var container=ender.toInventory(27);if(container!=null){var counts=MaterialListUtils.getInventoryItemCounts(container);add(next,counts);add(enderCounts,counts);}}
            nativeEnderItems=enderCounts;
            if(!inventory.equals(next)){inventory=next;generation++;}
            if(changed){var copy=new ArrayList<ItemStack>(source.getContainerSize());for(int n=0;n<source.getContainerSize();n++)copy.add(source.getItem(n).copy());inventorySnapshot=copy;inventoryOwner=source;}
            lastEnderCache=ender;lastUseEnder=useEnder;
        }
    }
    private static void add(Map<ItemType,Long> to,it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<ItemType> values){values.object2IntEntrySet().forEach(e->to.merge(e.getKey(),(long)e.getIntValue(),Long::sum));}
    public static long stored(ItemStack stack){return tracked.getOrDefault(new ItemType(stack,true,false),0L);}
    private static MaterialListBase activeList(){return fi.dy.masa.litematica.data.DataManager.getMaterialList();}
    private static Map<ItemType,Long> selected(Map<String,Map<ItemType,Long>> groups,Map<ItemType,Long> all,String selection){return selection.equals(StorageGroups.ALL)?all:groups.getOrDefault(selection,Map.of());}
    public static long available(ItemStack stack){return available(stack,activeList());}
    public static long available(ItemStack stack,MaterialListBase list){var key=new ItemType(stack,true,false);return inventory.getOrDefault(key,0L)+(combined()?selected(grouped,tracked,StorageGroups.selection(currentServer,list)).getOrDefault(key,0L):0);}
    public static int displayed(MaterialListEntry entry){return displayed(entry,activeList());}
    public static int displayed(MaterialListEntry entry,MaterialListBase list){return combined()?(int)Math.min(Integer.MAX_VALUE,available(entry.getStack(),list)):entry.getCountAvailable();}
    public static int displayWidthCount(MaterialListEntry row){var key=new ItemType(row.getStack(),true,false);return combined()?(int)Math.min(Integer.MAX_VALUE,inventory.getOrDefault(key,0L)+tracked.getOrDefault(key,0L)):row.getCountAvailable();}
    public record Breakdown(long inventory,long ender,long storages){public long total(){return inventory+ender+storages;}}
    public static Breakdown breakdown(ItemStack stack,MaterialListBase list){
        var key=new ItemType(stack,true,false);String selection=StorageGroups.selection(currentServer,list);
        long stored=combined()?selected(grouped,tracked,selection).getOrDefault(key,0L):0;
        long personal=combined()?selected(personalGroups,personalItems,selection).getOrDefault(key,0L):0;
        return new Breakdown(playerItems.getOrDefault(key,0L),nativeEnderItems.getOrDefault(key,0L)+personal,Math.max(0,stored-personal));
    }
    public static long required(MaterialListEntry entry,MaterialListBase list){return list.getMultiplier()==1?Math.max(0,entry.getCountMissing()):(long)entry.getCountTotal()*Math.max(1,list.getMultiplier());}
    public static int remaining(MaterialListEntry entry,MaterialListBase list){return (int)Math.min(Integer.MAX_VALUE,Math.max(0,required(entry,list)-available(entry.getStack(),list)));}
    private static View view(MaterialListBase list){
        String selection=StorageGroups.selection(currentServer,list);long revision=((ResourceListRevision)list).storages$revision();var source=list.getMaterialsAll();
        var cached=views.get(list);if(cached!=null&&cached.generation()==generation&&cached.revision()==revision&&cached.source()==source&&cached.selection().equals(selection))return cached;
        var ignored=((dev.asaul.litematicainventories.mixin.ResourceMaterialListAccess)list).storages$ignored();
        var rows=new ArrayList<MaterialListEntry>();long required=0,collected=0;
        for(var row:source){
            if(ignored.contains(row))continue;
            long need=required(row,list),have=Math.max(0,available(row.getStack(),list));
            required+=need;collected+=Math.min(need,have);if(need>have)rows.add(row);
        }
        rows.sort(new MaterialListSorter(list));
        var progress=new Progress(required,collected,Math.max(0,required-collected),required==0?100:100.0*collected/required);
        var result=new View(generation,revision,selection,source,rows,progress);views.put(list,result);return result;
    }
    public static List<MaterialListEntry> hudEntries(MaterialListBase list){return view(list).hud();}
    public record Progress(long required,long collected,long remaining,double percent){}
    public static Progress progress(MaterialListBase list){return view(list).progress();}
    private ResourceAccounting(){}
}
