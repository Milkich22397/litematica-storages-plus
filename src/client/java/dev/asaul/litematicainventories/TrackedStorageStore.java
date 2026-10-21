package dev.asaul.litematicainventories;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** Independent, permanent resource ledger. All mutations and codec work are on the client thread. */
public final class TrackedStorageStore {
    public record Key(String server,String dimension,String identity) {}
    public record Entry(Key key,List<BlockPos> positions,List<String> states,int size,int dataVersion,CompoundTag data,boolean valid,String label,String group,long observedAt) {
        public Entry(Key key,List<BlockPos> positions,List<String> states,int size,int dataVersion,CompoundTag data,boolean valid,String label,String group){this(key,positions,states,size,dataVersion,data,valid,label,group,0);}
        public Entry(Key key,List<BlockPos> positions,List<String> states,int size,int dataVersion,CompoundTag data,boolean valid){this(key,positions,states,size,dataVersion,data,valid,"","");}
        public Entry {positions=List.copyOf(positions);states=List.copyOf(states);data=data.copy();label=label==null?"":label;group=group==null?"":group;}
        @Override public CompoundTag data(){return data.copy();}
        public boolean personal(){return key.dimension().equals("player");}
    }
    private static final Map<Key,Entry> entries=new LinkedHashMap<>();
    private static final Map<CacheKey,Key> positions=new HashMap<>();
    private static final Path FILE=StorageSettings.DIRECTORY.resolve("tracked-storages.nbt");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"StoragesPlus-ledger");t.setDaemon(true);return t;});
    private static boolean loaded,primaryValid,dirty;
    private static final java.util.concurrent.atomic.AtomicBoolean writeFailed=new java.util.concurrent.atomic.AtomicBoolean();
    private static long revision,due;
    private static int validationTicks;
    public static volatile String error="";
    private static void failure(String message,Exception e){error=message+": "+e.getMessage();LoggerFactory.getLogger("StoragesPlus").error(message,e);}
    public static void load(){
        if(loaded)return;loaded=true;
        for(Path p:List.of(FILE,FILE.resolveSibling("tracked-storages.nbt.bak"))){
            if(!Files.exists(p))continue;
            try{var root=NbtIo.readCompressed(p,NbtAccounter.create(256L*1024*1024));var restored=decode(root);StorageGroups.restore(root.getCompoundOrEmpty("Organization"));entries.putAll(restored);entries.values().forEach(TrackedStorageStore::index);primaryValid=p.equals(FILE);if(!primaryValid)error="Recovered tracked storage backup";revision++;return;}
            catch(Exception e){failure("Cannot load tracked storages "+p,e);}
        }
    }
    public static long revision(){load();return revision;}
    public static List<Entry> snapshot(){load();return List.copyOf(entries.values());}
    public static boolean personal(ContainerRecorder.Opening o){return o.states.getFirst().is(Blocks.ENDER_CHEST);}
    public static Key key(ContainerRecorder.Opening o){return o.trackingKey;}
    static Key createKey(ContainerRecorder.Opening o){
        if(personal(o))return new Key(o.server,"player",o.playerId.toString());
        String id=o.adapter.halves().stream().map(BlockPos::asLong).sorted().map(String::valueOf).reduce((a,b)->a+","+b).orElseThrow();
        return new Key(o.server,o.dimension,id);
    }
    private static void index(Entry e){for(BlockPos p:e.positions)positions.put(new CacheKey(e.key.server,e.key.dimension,p),e.key);}
    private static Set<Key> matching(ContainerRecorder.Opening o){var keys=new LinkedHashSet<Key>();var k=key(o);if(entries.containsKey(k))keys.add(k);if(!personal(o))for(BlockPos p:o.adapter.halves()){var found=positions.get(new CacheKey(o.server,o.dimension,p));if(found!=null)keys.add(found);}return keys;}
    private static void removeMatching(ContainerRecorder.Opening o){for(var k:matching(o)){var old=entries.remove(k);if(old!=null)for(BlockPos p:old.positions)positions.remove(new CacheKey(k.server,k.dimension,p));}}
    public static boolean tracked(ContainerRecorder.Opening o){
        load();if(o==null)return false;if(entries.containsKey(key(o)))return true;
        if(!personal(o))for(var pos:o.adapter.halves())if(positions.containsKey(new CacheKey(o.server,o.dimension,pos)))return true;
        return false;
    }
    public static void toggle(ContainerRecorder.Opening o){
        load();if(o==null||!o.synchronizedMenu)return;
        if(tracked(o)){removeMatching(o);changed();flush();return;}
        if(entries.size()>=50000){error="Tracked storage limit reached (50000)";return;}
        var items=o.adapter.copyItems();if(observeNew(o,items))o.resourcePrevious=items;flush();
    }
    public static void observe(ContainerRecorder.Opening o,List<ItemStack> items){
        load();if(!StorageSettings.INSTANCE.resourceTracking||!o.synchronizedMenu||!tracked(o))return;
        var old=entries.get(key(o));
        if(old!=null&&old.valid&&old.dataVersion==net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()&&o.resourcePrevious!=null&&ItemStack.listMatches(o.resourcePrevious,items)){
            long now=System.currentTimeMillis();
            if(old.observedAt==0||now-old.observedAt>=60_000){entries.put(old.key,new Entry(old.key,old.positions,old.states,old.size,old.dataVersion,old.data,true,old.label,old.group,now));changed();}
            return;
        }
        if(observeNew(o,items))o.resourcePrevious=items;
    }
    private static boolean observeNew(ContainerRecorder.Opening o,List<ItemStack> items){
        try{
            var k=key(o);var data=InventoryData.encode(items,o.world.registryAccess());
            var states=personal(o)?List.<String>of():o.states.stream().map(ContainerMenuAdapter::identityState).toList();var old=entries.get(k);
            int version=net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
            if(old!=null&&old.valid&&old.dataVersion==version&&old.states.equals(states)&&old.data.equals(data)){long now=System.currentTimeMillis();if(old.observedAt==0||now-old.observedAt>=60_000){entries.put(k,new Entry(k,old.positions,old.states,old.size,old.dataVersion,old.data,true,old.label,old.group,now));changed();}return true;}
            // Keep labels/groups when a double chest topology changes.
            var details=old; if(details==null)for(var matched:matching(o)){details=entries.get(matched);if(details!=null)break;}
            removeMatching(o);
            var entry=new Entry(k,personal(o)?List.of():o.adapter.halves(),personal(o)?List.of():states,items.size(),version,data,true,details==null?"":details.label(),details==null?"":details.group(),System.currentTimeMillis());entries.put(k,entry);index(entry);changed();return true;
        }catch(RuntimeException e){failure("Cannot serialize tracked storage",e);return false;}
    }
    public static Entry get(Key key){load();return entries.get(key);}
    public static void edit(Key key,String label,String group){load();var e=entries.get(key);if(e==null)return;String name=label.strip();if(name.length()>80)name=name.substring(0,80);if(!group.isEmpty()&&!StorageGroups.groups(key.server()).containsKey(group))return;if(e.label.equals(name)&&e.group.equals(group))return;entries.put(key,new Entry(key,e.positions,e.states,e.size,e.dataVersion,e.data,e.valid,name,group,e.observedAt));changed();flush();}
    public static void remove(Key key){load();var old=entries.remove(key);if(old==null)return;for(var pos:old.positions)positions.remove(new CacheKey(key.server,key.dimension,pos));changed();flush();}
    public static void ungroup(String server,String group){load();for(var item:entries.entrySet()){var e=item.getValue();if(e.key.server.equals(server)&&e.group.equals(group))item.setValue(new Entry(e.key,e.positions,e.states,e.size,e.dataVersion,e.data,e.valid,e.label,"",e.observedAt));}changed();}
    /** Explicit cleanup only; legacy entries without a timestamp are preserved. */
    public static int removeOlderThan(long hours){if(hours<1||hours>876000)return 0;load();long cutoff=System.currentTimeMillis()-hours*3600000L;var keys=entries.values().stream().filter(e->e.observedAt>0&&e.observedAt<cutoff).map(Entry::key).toList();for(var key:keys){var e=entries.remove(key);for(var pos:e.positions)positions.remove(new CacheKey(key.server,key.dimension,pos));}if(!keys.isEmpty()){changed();flush();}return keys.size();}
    public static void organizationChanged(){load();changed();flush();}
    private static void changed(){revision++;if(!dirty)due=System.currentTimeMillis()+500;dirty=true;}
    public static void clear(){load();entries.clear();positions.clear();changed();flush();}
    public static void validateLoaded(Minecraft c){
        load();if(c.level==null||c.player==null)return;String server=SessionContext.serverId(c),dim=SessionContext.dimensionId(c);
        for(var e:entries.values()){
            if(!e.valid||e.personal()||!e.key.server().equals(server)||!e.key.dimension().equals(dim))continue;
            for(int n=0;n<e.positions.size();n++){
                BlockPos pos=e.positions.get(n);
                if(ClientChunks.loaded(c.level,pos)&&!ContainerMenuAdapter.identityState(c.level.getBlockState(pos)).equals(e.states.get(n))){invalidate(e);break;}
            }
        }
    }
    private static void invalidate(Entry e){entries.put(e.key,new Entry(e.key,e.positions,e.states,e.size,e.dataVersion,e.data,false,e.label,e.group,e.observedAt));changed();}
    public static void blockChanged(BlockPos pos){
        var c=Minecraft.getInstance();if(c.level==null||!ClientChunks.loaded(c.level,pos))return;
        String server=SessionContext.serverId(c),dim=SessionContext.dimensionId(c);
        var key=positions.get(new CacheKey(server,dim,pos));var e=key==null?null:entries.get(key);
        if(e!=null&&e.valid){int n=e.positions.indexOf(pos);if(n>=0&&!ContainerMenuAdapter.identityState(c.level.getBlockState(pos)).equals(e.states.get(n)))invalidate(e);}
    }
    public static boolean current(Entry e,Minecraft c){
        return c.level!=null&&c.player!=null&&current(e,SessionContext.serverId(c),c.player.getUUID().toString());
    }
    static boolean current(Entry e,String server,String player){return e.valid&&e.dataVersion==net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()&&e.key.server().equals(server)&&(!e.personal()||e.key.identity().equals(player));}
    public static void tick(Minecraft c){if(writeFailed.getAndSet(false)){dirty=true;due=System.currentTimeMillis()+1000;}if(++validationTicks>=20){validationTicks=0;validateLoaded(c);}if(dirty&&System.currentTimeMillis()>=due)flush();}
    public static void flush(){load();if(!dirty)return;var detached=encode(entries);dirty=false;IO.submit(()->write(detached));}
    public static void awaitWrites(){flush();try{IO.submit(()->{}).get(15,TimeUnit.SECONDS);}catch(Exception e){failure("Tracked storage flush failed",e);}}
    private static void write(CompoundTag data){
        try{
            Files.createDirectories(FILE.getParent());
            if(!primaryValid&&Files.exists(FILE))Files.copy(FILE,FILE.resolveSibling("tracked-unreadable-"+System.currentTimeMillis()+".nbt"));
            Path temp=FILE.resolveSibling("tracked-storages.nbt.tmp"),backupTemp=FILE.resolveSibling("tracked-storages.backup.tmp");
            NbtIo.writeCompressed(data,temp);try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            PersistentInventoryStore.replace(temp,FILE);primaryValid=true;
            Files.copy(FILE,backupTemp,StandardCopyOption.REPLACE_EXISTING);PersistentInventoryStore.replace(backupTemp,FILE.resolveSibling("tracked-storages.nbt.bak"));error="";
        }catch(Exception e){writeFailed.set(true);failure("Cannot save tracked storages",e);}
    }
    public static CompoundTag encode(Map<Key,Entry> source){
        var root=new CompoundTag();root.putInt("Schema",1);var list=new ListTag();
        for(var e:source.values()){
            var n=new CompoundTag();n.putString("World",e.key.server());n.putString("Dimension",e.key.dimension());n.putString("Identity",e.key.identity());n.putInt("Size",e.size);n.putInt("DataVersion",e.dataVersion);n.putBoolean("Valid",e.valid);n.putString("Label",e.label);n.putString("Group",e.group);n.putLong("Observed",e.observedAt);n.put("Data",e.data());
            var positions=new ListTag();for(int i=0;i<e.positions.size();i++){var p=new CompoundTag();p.putLong("Pos",e.positions.get(i).asLong());p.putString("State",e.states.get(i));positions.add(p);}n.put("Positions",positions);list.add(n);
        }
        root.put("Entries",list);root.put("Organization",StorageGroups.encode());return root;
    }
    public static Map<Key,Entry> decode(CompoundTag root){
        if(root.getIntOr("Schema",0)!=1)throw new IllegalArgumentException("Unknown tracked storage schema");
        var restored=new LinkedHashMap<Key,Entry>();var occupied=new HashSet<CacheKey>();
        for(var raw:root.getListOrEmpty("Entries")){
            if(!(raw instanceof CompoundTag e))throw new IllegalArgumentException("Invalid tracked storage");
            var k=new Key(e.getStringOr("World",""),e.getStringOr("Dimension",""),e.getStringOr("Identity",""));
            int size=e.getIntOr("Size",0);var data=e.getCompoundOrEmpty("Data");
            if(k.server.isBlank()||k.dimension.isBlank()||k.identity.isBlank()||size<1||size>54||!(data.get("Items") instanceof ListTag))throw new IllegalArgumentException("Invalid tracked inventory");
            var positions=new ArrayList<BlockPos>();var states=new ArrayList<String>();for(var p:e.getListOrEmpty("Positions")){if(!(p instanceof CompoundTag n))throw new IllegalArgumentException("Invalid position");positions.add(BlockPos.of(n.getLongOr("Pos",0)));states.add(n.getStringOr("State",""));}
            if(k.dimension.equals("player")?!positions.isEmpty():positions.size()<1||positions.size()>2||positions.size()!=new HashSet<>(positions).size())throw new IllegalArgumentException("Invalid topology");
            if(k.dimension.equals("player")){UUID.fromString(k.identity);if(size!=27)throw new IllegalArgumentException("Invalid ender inventory");}
            else {
                String canonical=positions.stream().map(BlockPos::asLong).sorted().map(String::valueOf).reduce((a,b)->a+","+b).orElseThrow();
                if(!canonical.equals(k.identity)||states.stream().anyMatch(String::isBlank)||(positions.size()==2?size!=54:!Set.of(3,5,9,27).contains(size)))throw new IllegalArgumentException("Invalid container identity");
                for(BlockPos pos:positions)if(!occupied.add(new CacheKey(k.server,k.dimension,pos)))throw new IllegalArgumentException("Overlapping tracked storages");
            }
            var entry=new Entry(k,positions,states,size,e.getIntOr("DataVersion",0),data,e.getBooleanOr("Valid",false),e.getStringOr("Label",""),e.getStringOr("Group",""),Math.max(0,e.getLongOr("Observed",0)));
            if(restored.put(k,entry)!=null||restored.size()>50000)throw new IllegalArgumentException("Duplicate/oversized ledger");
        }
        return restored;
    }
    private TrackedStorageStore(){}
}
