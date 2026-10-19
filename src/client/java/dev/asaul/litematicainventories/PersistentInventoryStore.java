package dev.asaul.litematicainventories;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** One ordered writer, detached NBT only. Never touches client registries off thread. */
public final class PersistentInventoryStore {
    private static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r,"StoragePreview-cache"); t.setDaemon(true); return t; });
    private static ScheduledFuture<?> pending;
    private static final Path FILE = StorageSettings.DIRECTORY.resolve("containers.nbt");
    public static volatile String error = "";
    private static boolean primaryValid;
    private static final java.util.concurrent.atomic.AtomicBoolean writeFailed=new java.util.concurrent.atomic.AtomicBoolean();
    public static boolean takeWriteFailure(){return writeFailed.getAndSet(false);}
    public static java.util.concurrent.CompletableFuture<Map<CacheKey,ContainerSnapshot>> loadAsync() { return java.util.concurrent.CompletableFuture.supplyAsync(PersistentInventoryStore::load,IO); }
    public static Map<CacheKey,ContainerSnapshot> load() {
        for (Path p : List.of(FILE, FILE.resolveSibling("containers.nbt.bak"))) {
            if (!Files.exists(p)) continue;
            try {
                CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.create(256L*1024*1024));
                if (root.getIntOr("Schema",0) != 1) throw new IllegalArgumentException("Unsupported cache schema");
                Map<CacheKey,ContainerSnapshot> result = new HashMap<>();
                for (Tag raw : root.getListOrEmpty("Entries")) {
                    if (!(raw instanceof CompoundTag e)) throw new IllegalArgumentException("Invalid entry");
                    int slots = e.getIntOr("Size",0);
                    if (slots < 1 || slots > 54 || !e.getCompoundOrEmpty("Data").contains("Items")) throw new IllegalArgumentException("Invalid inventory");
                    CacheKey key = new CacheKey(e.getStringOr("World",""),e.getStringOr("Dimension",""),BlockPos.of(e.getLongOr("Pos",0)));
                    result.put(key, new ContainerSnapshot(e.getStringOr("Block",""),e.getStringOr("State",""),slots,e.getLongOr("Observed",0),e.getIntOr("DataVersion",0),e.getCompoundOrEmpty("Data")));
                    if (result.size() > 200000) throw new IllegalArgumentException("Cache entry limit");
                }
                primaryValid = p.equals(FILE);
                if (!primaryValid) error = "Recovered cache backup";
                return result;
            } catch (Exception ex) { error = "Cannot read cache: " + ex.getMessage(); LoggerFactory.getLogger("StoragePreview").error("Cache read failed: {}",p,ex); }
        }
        return new HashMap<>();
    }
    public static CompoundTag encode(Map<CacheKey,ContainerSnapshot> cache) {
        CompoundTag root = new CompoundTag(); root.putInt("Schema",1); ListTag entries = new ListTag();
        cache.forEach((k,s) -> { CompoundTag e=new CompoundTag(); e.putString("World",k.server()); e.putString("Dimension",k.dimension()); e.putLong("Pos",k.pos().asLong()); e.putString("Block",s.blockId()); e.putString("State",s.state()); e.putInt("Size",s.slots()); e.putLong("Observed",s.observedAt()); e.putInt("DataVersion",s.dataVersion()); e.put("Data",s.blockEntityData()); entries.add(e); });
        root.put("Entries",entries); return root;
    }
    public static synchronized void schedule(CompoundTag detached, boolean immediate) {
        if (pending != null) pending.cancel(false);
        pending = IO.schedule(() -> write(detached), immediate ? 0 : 750, TimeUnit.MILLISECONDS);
    }
    private static void write(CompoundTag tag) {
        try {
            Files.createDirectories(FILE.getParent());
            if(!primaryValid && Files.exists(FILE)) Files.copy(FILE,FILE.resolveSibling("containers.unreadable-"+System.currentTimeMillis()+".nbt"),StandardCopyOption.REPLACE_EXISTING);
            Path temp=FILE.resolveSibling("containers.nbt.tmp");
            NbtIo.writeCompressed(tag,temp);
            try (var channel = java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
            replace(temp,FILE); primaryValid=true;
            // Mirror the successful generation: a clear must not resurrect deleted data via backup.
            Path bt=FILE.resolveSibling("backup.tmp"); Files.copy(FILE,bt,StandardCopyOption.REPLACE_EXISTING); replace(bt,FILE.resolveSibling("containers.nbt.bak"));
            error="";
        } catch (Exception ex) { writeFailed.set(true);error="Cache write failed: "+ex.getMessage(); LoggerFactory.getLogger("StoragePreview").error(error,ex); }
    }
    public static void replace(Path from, Path to) throws java.io.IOException {
        try { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from,to,StandardCopyOption.REPLACE_EXISTING); }
    }
    public static void awaitWrites() {
        try {
            ScheduledFuture<?> scheduled; synchronized(PersistentInventoryStore.class){scheduled=pending;}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            if(scheduled!=null&&!scheduled.isCancelled())scheduled.get(10,TimeUnit.SECONDS);
            IO.submit(() -> {}).get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
        } catch (Exception e) { error="Cache flush failed: "+e.getMessage();LoggerFactory.getLogger("StoragePreview").error(error,e); }
    }
}
