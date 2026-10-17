package dev.asaul.litematicainventories;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.*;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

public final class SchematicInventoryResolver {
    public record Expected(BlockPos pos, BlockState state, List<ItemStack> items, boolean known, boolean conflict, long observed, String source) {
        public boolean stale() {return observed>0 && StorageSettings.INSTANCE.ttlHours>0 && System.currentTimeMillis()-observed>StorageSettings.INSTANCE.ttlHours*3600000L;}
    }
    private static BlockPos minOffset(LitematicaSchematic s,String region) {
        BlockPos end=PositionUtils.getRelativeEndPositionFromAreaSize(s.getAreaSize(region));
        return PositionUtils.getMinCorner(BlockPos.ZERO,end);
    }
    /** Mirrors LitematicaSchematic.placeBlocksToWorld: global then subregion, relative to signed corner. */
    public static BlockPos world(SchematicPlacement p,String region,BlockPos local) {
        var sub=p.getRelativeSubRegionPlacement(region);
        return PositionUtils.getTransformedPlacementPosition(local.offset(minOffset(p.getSchematic(),region)),p,sub)
                .offset(PositionUtils.getTransformedBlockPos(sub.getPos(),p.getMirror(),p.getRotation())).offset(p.getOrigin());
    }
    public static BlockPos local(SchematicPlacement p,String region,BlockPos world) {
        var sub=p.getRelativeSubRegionPlacement(region);
        BlockPos d=world.subtract(p.getOrigin()).subtract(PositionUtils.getTransformedBlockPos(sub.getPos(),p.getMirror(),p.getRotation()));
        d=PositionUtils.getReverseTransformedBlockPos(d,sub.getMirror(),sub.getRotation());
        return PositionUtils.getReverseTransformedBlockPos(d,p.getMirror(),p.getRotation()).subtract(minOffset(p.getSchematic(),region));
    }
    public static Expected read(LitematicaSchematic s,String region,BlockPos local,BlockPos pos,String source) {
        var c=s.getSubRegionContainer(region); BlockState state=c.get(local.getX(),local.getY(),local.getZ());int size=ContainerMenuAdapter.size(state);
        if(size==0) return null;
        var map=s.getBlockEntityMapForRegion(region); var data=map==null?null:map.get(local);
        if(data==null) return new Expected(pos,state,List.of(),false,false,0,source);
        var tag=DataConverterNbt.toVanillaCompound(data); var meta=tag.getCompoundOrEmpty(InventoryData.OBSERVATION);
        long time=meta.getLongOr("Observed",0);
        if(meta.contains("Schema") && (meta.getIntOr("Schema",0)!=1 || !meta.getBooleanOr("Complete",false) || meta.getIntOr("Size",0)!=size)) return new Expected(pos,state,List.of(),false,false,time,source+" (incomplete)");
        try {
            var client=Minecraft.getInstance(); var registries=client.level!=null?client.level.registryAccess():fi.dy.masa.litematica.world.SchematicWorldHandler.INSTANCE.getRegistryManager();
            return new Expected(pos,state,InventoryData.decode(tag,size,registries),true,false,time,source);
        } catch(Exception ex) { return new Expected(pos,state,List.of(),false,false,time,source+" (unknown item/component)"); }
    }
    public static Expected transform(Expected e,SchematicPlacement p,String r) {
        var sub=p.getRelativeSubRegionPlacement(r);var mirror=sub.getMirror();
        if(mirror!=net.minecraft.world.level.block.Mirror.NONE && (p.getRotation()==net.minecraft.world.level.block.Rotation.CLOCKWISE_90||p.getRotation()==net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90)) mirror=mirror==net.minecraft.world.level.block.Mirror.FRONT_BACK?net.minecraft.world.level.block.Mirror.LEFT_RIGHT:net.minecraft.world.level.block.Mirror.FRONT_BACK;
        var state=e.state.mirror(p.getMirror()).mirror(mirror).rotate(p.getRotation().getRotated(sub.getRotation()));
        return new Expected(e.pos,state,e.items,e.known,e.conflict,e.observed,e.source);
    }
    public static Expected at(SchematicPlacement p,BlockPos pos) {
        if(!p.isEnabled()) return null;
        Expected result=null;
        for(String r:new TreeSet<>(p.getEnabledRelativeSubRegionPlacements().keySet())) {
            BlockPos l=local(p,r,pos);var size=p.getSchematic().getAreaSize(r);
            if(l.getX()<0||l.getY()<0||l.getZ()<0||l.getX()>=Math.abs(size.getX())||l.getY()>=Math.abs(size.getY())||l.getZ()>=Math.abs(size.getZ())) continue;
            Expected e=read(p.getSchematic(),r,l,pos,p.getName()+" / "+r); if(e!=null) result=merge(result,transform(e,p,r));
        }
        return result;
    }
    public static boolean compatible(BlockState expected,BlockState actual) {
        if(expected.getBlock()!=actual.getBlock())return false;
        if(expected.getBlock() instanceof net.minecraft.world.level.block.ChestBlock){
            var type=expected.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
            return type==actual.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)&&(type==net.minecraft.world.level.block.state.properties.ChestType.SINGLE||expected.getValue(net.minecraft.world.level.block.ChestBlock.FACING)==actual.getValue(net.minecraft.world.level.block.ChestBlock.FACING));
        }
        return true;
    }
    public static Expected merge(Expected a,Expected b) {
        if(a==null)return b;
        if(a.conflict || b.conflict || a.known!=b.known || !a.state.equals(b.state) || a.known&&!ItemStack.listMatches(a.items,b.items)) return new Expected(a.pos,a.state,List.of(),false,true,0,a.source+" / "+b.source);
        return a;
    }
    public record Resolution(SchematicPlacement placement, List<Expected> halves, String status) {}
    public static Resolution resolve(ContainerRecorder.Opening o) {
        var manager=DataManager.getSchematicPlacementManager(); var selected=manager.getSelectedSchematicPlacement();
        List<SchematicPlacement> candidates=manager.getAllSchematicsPlacements().stream().filter(SchematicPlacement::isEnabled).filter(p->o.adapter.halves().stream().anyMatch(pos->at(p,pos)!=null)).sorted(Comparator.comparing(p->p.getHashId().toString())).toList();
        SchematicPlacement p=selected!=null&&candidates.contains(selected)?selected:candidates.size()==1?candidates.getFirst():null;
        if(p==null) return new Resolution(null,List.of(),candidates.isEmpty()?"no_placement":"ambiguous");
        List<Expected> halves=new ArrayList<>();
        for(int i=0;i<o.adapter.halves().size();i++) {
            Expected e=at(p,o.adapter.halves().get(i));
            if(e!=null && !compatible(e.state,o.states.get(i))) e=new Expected(e.pos,e.state,List.of(),false,true,0,e.source);
            halves.add(e);
        }
        return new Resolution(p,Collections.unmodifiableList(halves),"ready");
    }
}
