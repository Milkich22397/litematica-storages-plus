package dev.asaul.litematicainventories;
import dev.asaul.litematicainventories.mixin.*;
import fi.dy.masa.litematica.materials.*;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.*;

/** Independent view model: never mutates Litematica's building-material list. */
public final class StorageMaterialList {
    public record Row(InventoryComparator.Key key,long expected,long actual,long missing,long extra) {}
    public final MaterialListBase original;
    public final LitematicaSchematic schematic;
    public final SchematicPlacement placement;
    public final Collection<String> regions;
    public List<Row> rows=List.of();
    public record OfflineKey(net.minecraft.nbt.CompoundTag identity) {public OfflineKey {identity=identity.copy();}}
    public record OfflineRow(OfflineKey key,String id,long expected) {}
    public List<OfflineRow> offlineRows=List.of();
    public boolean rawOffline;
    public int expectedKnown, expectedUnknown, actualKnown, actualUnknown, stale, conflicts, live;
    public boolean bound;
    public StorageMaterialList(MaterialListBase list) {
        original=list;
        if(list instanceof MaterialListPlacementAccess a) {placement=a.storage$placement();schematic=placement.getSchematic();regions=placement.getEnabledRelativeSubRegionPlacements().keySet();}
        else {var a=(MaterialListSchematicAccess)list;placement=null;schematic=a.storage$schematic();regions=a.storage$regions();}
    }
    public static boolean supports(MaterialListBase list) {return list instanceof MaterialListSchematicAccess || list instanceof MaterialListPlacementAccess;}
    public void refresh() {
        expectedKnown=expectedUnknown=actualKnown=actualUnknown=stale=conflicts=live=0;
        var client=Minecraft.getInstance(); rawOffline=client.level==null; if(rawOffline){refreshOffline();return;} offlineRows=List.of(); bound=placement!=null && placement.isEnabled() && client.level!=null;
        Map<BlockPos,SchematicInventoryResolver.Expected> containers=new LinkedHashMap<>();
        Collection<String> enabled=placement!=null?placement.getEnabledRelativeSubRegionPlacements().keySet():regions;
        for(String r:new TreeSet<>(enabled)) {
            var c=schematic.getSubRegionContainer(r);if(c==null)continue;var size=schematic.getAreaSize(r);
            BlockPos region=schematic.getSubRegionPosition(r),offset=PositionUtils.getMinCorner(BlockPos.ZERO,PositionUtils.getRelativeEndPositionFromAreaSize(size));
            for(int y=0;y<Math.abs(size.getY());y++) for(int z=0;z<Math.abs(size.getZ());z++) for(int x=0;x<Math.abs(size.getX());x++) {
                if(ContainerMenuAdapter.size(c.get(x,y,z))==0)continue;
                BlockPos l=new BlockPos(x,y,z);BlockPos pos=placement!=null?SchematicInventoryResolver.world(placement,r,l):region.offset(offset).offset(l);
                var e=SchematicInventoryResolver.read(schematic,r,l,pos,(placement!=null?placement.getName():schematic.getMetadata().getName())+" / "+r);
                if(placement!=null)e=SchematicInventoryResolver.transform(e,placement,r);
                containers.merge(pos,e,SchematicInventoryResolver::merge);
            }
        }
        Map<InventoryComparator.Key,long[]> sum=new LinkedHashMap<>();Set<BlockPos> done=new HashSet<>();
        for(var entry:containers.entrySet()) {
            if(!done.add(entry.getKey()))continue;
            var first=entry.getValue();List<SchematicInventoryResolver.Expected> group=new ArrayList<>();group.add(first);
            if(first.state().getBlock() instanceof ChestBlock && first.state().getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
                BlockPos other=ChestBlock.getConnectedBlockPos(first.pos(),first.state());var next=containers.get(other);
                if(next!=null && next.state().getBlock()==first.state().getBlock() && next.state().getValue(ChestBlock.TYPE)!=ChestType.SINGLE && next.state().getValue(ChestBlock.TYPE)!=first.state().getValue(ChestBlock.TYPE) && ChestBlock.getConnectedBlockPos(other,next.state()).equals(first.pos())) {if(done.add(other))group.add(next);}
                else group.add(null);
            }
            List<ItemStack> expected=new ArrayList<>(),actual=new ArrayList<>();boolean complete=true;
            for(var e:group) {
                if(e==null) {expectedUnknown++;actualUnknown++;complete=false;continue;}
                if(e.conflict())conflicts++;
                if(!e.known()) {expectedUnknown++;complete=false;
                    List<ItemStack> a=bound?actual(e):null; if(a==null)actualUnknown++;else{actualKnown++;actual.addAll(a);} continue;}
                expectedKnown++;expected.addAll(e.items());
                List<ItemStack> a=bound?actual(e):null;
                if(a==null) {actualUnknown++;complete=false;} else {actualKnown++;actual.addAll(a);}
            }
            long multiplier=Math.max(1,original.getMultiplier());
            var ec=ComparisonEngine.counts(expected.stream().map(InventoryComparator::amount).toList());
            var ac=ComparisonEngine.counts(actual.stream().map(InventoryComparator::amount).toList());
            Set<InventoryComparator.Key> keys=new LinkedHashSet<>(ec.keySet());keys.addAll(ac.keySet());
            for(var key:keys) {long e=ec.getOrDefault(key,0L)*multiplier,a=ac.getOrDefault(key,0L);long[] values=sum.computeIfAbsent(key,k->new long[4]);values[0]+=e; values[1]+=a; if(complete&&bound) {values[2]+=Math.max(0,e-a);values[3]+=Math.max(0,a-e);} }
        }
        List<Row> result=new ArrayList<>();sum.forEach((k,v)->result.add(new Row(k,v[0],v[1],v[2],v[3])));rows=result;
    }
    private void refreshOffline() {
        bound=false; rows=List.of();
        Map<BlockPos,net.minecraft.nbt.CompoundTag> inventories=new LinkedHashMap<>();Set<BlockPos> unknown=new HashSet<>(),bad=new HashSet<>();
        Collection<String> enabled=placement!=null?placement.getEnabledRelativeSubRegionPlacements().keySet():regions;
        for(String r:new TreeSet<>(enabled)) {
            var c=schematic.getSubRegionContainer(r);if(c==null)continue;var size=schematic.getAreaSize(r);
            var offset=PositionUtils.getMinCorner(BlockPos.ZERO,PositionUtils.getRelativeEndPositionFromAreaSize(size));
            for(int y=0;y<Math.abs(size.getY());y++)for(int z=0;z<Math.abs(size.getZ());z++)for(int x=0;x<Math.abs(size.getX());x++) {
                int slots=ContainerMenuAdapter.size(c.get(x,y,z));if(slots==0)continue;BlockPos l=new BlockPos(x,y,z);
                BlockPos pos=placement!=null?SchematicInventoryResolver.world(placement,r,l):schematic.getSubRegionPosition(r).offset(offset).offset(l);
                var map=schematic.getBlockEntityMapForRegion(r);var be=map==null?null:map.get(l);
                var raw=be==null?new net.minecraft.nbt.CompoundTag():fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt.toVanillaCompound(be);
                var meta=raw.getCompoundOrEmpty(InventoryData.OBSERVATION);
                boolean known=raw.get("Items") instanceof net.minecraft.nbt.ListTag;
                if(meta.contains("Schema"))known &= meta.getIntOr("Schema",0)==1&&meta.getBooleanOr("Complete",false)&&meta.getIntOr("Size",0)==slots;
                if(!known){unknown.add(pos);continue;}
                net.minecraft.nbt.CompoundTag contents=new net.minecraft.nbt.CompoundTag();contents.put("Items",raw.get("Items").copy());
                var previous=inventories.putIfAbsent(pos,contents);if(previous!=null&&!previous.equals(contents))bad.add(pos);
                if(meta.getLongOr("Observed",0)>0&&StorageSettings.INSTANCE.ttlHours>0&&System.currentTimeMillis()-meta.getLongOr("Observed",0)>StorageSettings.INSTANCE.ttlHours*3600000L)stale++;
            }
        }
        for(BlockPos p:unknown)if(inventories.containsKey(p))bad.add(p);
        Map<OfflineKey,Long> counts=new LinkedHashMap<>();
        for(var entry:inventories.entrySet()) {
            if(bad.contains(entry.getKey()))continue;
            boolean valid=true;Map<OfflineKey,Long> per=new LinkedHashMap<>();Set<Integer> slots=new HashSet<>();
            for(var t:entry.getValue().getListOrEmpty("Items")) {
                if(!(t instanceof net.minecraft.nbt.CompoundTag item)){valid=false;break;}
                String id=item.getStringOr("id","");int slot=item.getIntOr("Slot",-1);int count=item.getIntOr("count",0);
                if(net.minecraft.resources.Identifier.tryParse(id)==null||slot<0||slot>=54||!slots.add(slot)||count<=0){valid=false;break;}
                var identity=item.copy();identity.remove("Slot");identity.remove("count");per.merge(new OfflineKey(identity),(long)count,Long::sum);
            }
            if(!valid){unknown.add(entry.getKey());continue;}
            expectedKnown++;per.forEach((k,v)->counts.merge(k,v*Math.max(1,original.getMultiplier()),Long::sum));
        }
        conflicts=bad.size();unknown.addAll(bad);expectedUnknown=unknown.size();actualUnknown=expectedKnown+expectedUnknown;
        List<OfflineRow> result=new ArrayList<>();counts.forEach((k,v)->result.add(new OfflineRow(k,k.identity.getStringOr("id","?"),v)));offlineRows=result;
    }
    private List<ItemStack> actual(SchematicInventoryResolver.Expected e) {
        var o=ContainerRecorder.active();
        if(o!=null&&o.synchronizedMenu) {int half=o.adapter.halves().indexOf(e.pos());if(half>=0){live++;return o.adapter.copyItems().subList(half*o.adapter.halfSize(),(half+1)*o.adapter.halfSize());}}
        var client=Minecraft.getInstance();if(ClientChunks.loaded(client.level,e.pos())&&!SchematicInventoryResolver.compatible(e.state(),client.level.getBlockState(e.pos())))return null;var s=SessionInventoryCache.get(new CacheKey(SessionContext.serverId(client),SessionContext.dimensionId(client),e.pos()));
        if(s==null||s.block()!=e.state().getBlock())return null;
        try {var items=InventoryData.decode(s.blockEntityData(),s.slots(),client.level.registryAccess());if(s.stale())stale++;return items;}catch(Exception ex){return null;}
    }
}
