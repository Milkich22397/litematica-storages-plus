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
    public record Entry(Key key,List<BlockPos> positions,List<String> states,int size,int dataVersion,CompoundTag data,boolean valid) {
        public Entry {positions=List.copyOf(positions);states=List.copyOf(states);data=data.copy();}
        @Override public CompoundTag data(){return data.copy();}
        public boolean personal(){return key.dimension().equals("player");}
    }
    private static final Map<Key,Entry> entries=new LinkedHashMap<>();
    private static final Map<CacheKey,Key> positions=new HashMap<>();
    private static final Path FILE=StorageSettings.DIRECTORY.resolve("tracked-storages.nbt");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"StoragesPlus-ledger");t.setDaemon(true);return t;});
    private static boolean loaded,primaryValid,dirty;
    private static volatile boolean writeFailed;
    private static long revision,due;
    public static volatile String error="";
    private static void failure(String message,Exception e){error=message+": "+e.getMessage();LoggerFactory.getLogger("StoragesPlus").error(message,e);}
    public static void load(){
        if(loaded)return;loaded=true;
        for(Path p:List.of(FILE,FILE.resolveSibling("tracked-storages.nbt.bak"))){
            if(!Files.exists(p))continue;
            try{var restored=decode(NbtIo.readCompressed(p,NbtAccounter.create(256L*1024*1024)));entries.putAll(restored);entries.values().forEach(TrackedStorageStore::index);primaryValid=p.equals(FILE);if(!primaryValid)error="Recovered tracked storage backup";revision++;return;}
            catch(Exception e){failure("Cannot load tracked storages "+p,e);}
        }
    }
    public static long revision(){load();return revision;}
    public static List<Entry> snapshot(){load();return List.copyOf(entries.values());}
    public static boolean personal(ContainerRecorder.Opening o){return o.states.getFirst().is(Blocks.ENDER_CHEST);}
    public static Key key(ContainerRecorder.Opening o){
        if(personal(o))return new Key(o.server,"player",o.playerId.toString());
        String id=o.adapter.halves().stream().map(BlockPos::asLong).sorted().map(String::valueOf).reduce((a,b)->a+","+b).orElseThrow();
        return new Key(o.server,o.dimension,id);
    }
    private static void index(Entry e){for(BlockPos p:e.positions)positions.put(new CacheKey(e.key.server,e.key.dimension,p),e.key);}
    private static Set<Key> matching(ContainerRecorder.Opening o){var keys=new HashSet<Key>();var k=key(o);if(entries.containsKey(k))keys.add(k);if(!personal(o))for(BlockPos p:o.adapter.halves()){var found=positions.get(new CacheKey(o.server,o.dimension,p));if(found!=null)keys.add(found);}return keys;}
    private static void removeMatching(ContainerRecorder.Opening o){for(var k:matching(o)){var old=entries.remove(k);if(old!=null)for(BlockPos p:old.positions)positions.remove(new CacheKey(k.server,k.dimension,p));}}
    public static boolean tracked(ContainerRecorder.Opening o){load();return o!=null&&!matching(o).isEmpty();}
    public static void toggle(ContainerRecorder.Opening o){
        load();if(o==null||!o.synchronizedMenu)return;
        if(tracked(o)){removeMatching(o);changed();flush();return;}
        if(entries.size()>=50000){error="Tracked storage limit reached (50000)";return;}
        observeNew(o,o.adapter.copyItems());flush();
    }
    public static void observe(ContainerRecorder.Opening o,List<ItemStack> items){
        load();if(!StorageSettings.INSTANCE.resourceTracking||!o.synchronizedMenu||!tracked(o))return;
        var old=entries.get(key(o));
        if(old!=null&&old.valid&&old.dataVersion==net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()&&o.resourcePrevious!=null&&ItemStack.listMatches(o.resourcePrevious,items))return;
        if(observeNew(o,items))o.resourcePrevious=items;
    }
    private static boolean observeNew(ContainerRecorder.Opening o,List<ItemStack> items){
        try{
            var k=key(o);var data=InventoryData.encode(items,o.world.registryAccess());
            var states=o.states.stream().map(ContainerMenuAdapter::identityState).toList();var old=entries.get(k);
            int version=net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
            if(old!=null&&old.valid&&old.dataVersion==version&&old.states.equals(states)&&old.data.equals(data))return true;
            // Topology changes replace overlapping observations, never add a second copy of a half.
            removeMatching(o);
            var entry=new Entry(k,personal(o)?List.of():o.adapter.halves(),personal(o)?List.of():states,items.size(),version,data,true);entries.put(k,entry);index(entry);changed();return true;
        }catch(RuntimeException e){failure("Cannot serialize tracked storage",e);return false;}
    }
    private static void changed(){revision++;if(!dirty)due=System.currentTimeMillis()+500;dirty=true;}
    public static void clear(){load();entries.clear();positions.clear();changed();flush();}
    public static void validateLoaded(Minecraft c){
        load();if(c.level==null||c.player==null)return;String server=SessionContext.serverId(c),dim=SessionContext.dimensionId(c);
        for(var e:new ArrayList<>(entries.values())){
            if(!e.valid||e.personal()||!e.key.server().equals(server)||!e.key.dimension().equals(dim))continue;
            for(int n=0;n<e.positions.size();n++){
                BlockPos pos=e.positions.get(n);
                if(ClientChunks.loaded(c.level,pos)&&!ContainerMenuAdapter.identityState(c.level.getBlockState(pos)).equals(e.states.get(n))){invalidate(e);break;}
            }
        }
    }
    private static void invalidate(Entry e){entries.put(e.key,new Entry(e.key,e.positions,e.states,e.size,e.dataVersion,e.data,false));changed();}
    public static void blockChanged(BlockPos pos){
        var c=Minecraft.getInstance();if(c.level==null||!ClientChunks.loaded(c.level,pos))return;
        String server=SessionContext.serverId(c),dim=SessionContext.dimensionId(c);
        var key=positions.get(new CacheKey(server,dim,pos));var e=key==null?null:entries.get(key);
        if(e!=null&&e.valid){int n=e.positions.indexOf(pos);if(n>=0&&!ContainerMenuAdapter.identityState(c.level.getBlockState(pos)).equals(e.states.get(n)))invalidate(e);}
    }
    public static boolean current(Entry e,Minecraft c){
        return c.level!=null&&c.player!=null&&e.valid&&e.dataVersion==net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()&&e.key.server().equals(SessionContext.serverId(c))&&(!e.personal()||e.key.identity().equals(c.player.getUUID().toString()));
    }
    public static void tick(Minecraft c){if(writeFailed){writeFailed=false;dirty=true;due=System.currentTimeMillis()+1000;}if(c.level!=null&&c.level.getGameTime()%20==0)validateLoaded(c);if(dirty&&System.currentTimeMillis()>=due)flush();}
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
        }catch(Exception e){writeFailed=true;failure("Cannot save tracked storages",e);}
    }
    public static CompoundTag encode(Map<Key,Entry> source){
        var root=new CompoundTag();root.putInt("Schema",1);var list=new ListTag();
        for(var e:source.values()){
            var n=new CompoundTag();n.putString("World",e.key.server());n.putString("Dimension",e.key.dimension());n.putString("Identity",e.key.identity());n.putInt("Size",e.size);n.putInt("DataVersion",e.dataVersion);n.putBoolean("Valid",e.valid);n.put("Data",e.data());
            var positions=new ListTag();for(int i=0;i<e.positions.size();i++){var p=new CompoundTag();p.putLong("Pos",e.positions.get(i).asLong());p.putString("State",e.states.get(i));positions.add(p);}n.put("Positions",positions);list.add(n);
        }
        root.put("Entries",list);return root;
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
            var entry=new Entry(k,positions,states,size,e.getIntOr("DataVersion",0),data,e.getBooleanOr("Valid",false));
            if(restored.put(k,entry)!=null||restored.size()>50000)throw new IllegalArgumentException("Duplicate/oversized ledger");
        }
        return restored;
    }
    private TrackedStorageStore(){}
}
