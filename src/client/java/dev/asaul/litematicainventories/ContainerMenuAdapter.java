package dev.asaul.litematicainventories;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.*;

/** Vanilla menus only. Menu id, logical slot and backing container index are distinct. */
public record ContainerMenuAdapter(List<Slot> slots, List<BlockPos> halves, int halfSize) {
    public static int size(BlockState s) {
        Block b=s.getBlock();
        if(b==Blocks.CHEST||b==Blocks.TRAPPED_CHEST||b==Blocks.BARREL||b instanceof ShulkerBoxBlock) return 27;
        if(b==Blocks.FURNACE||b==Blocks.BLAST_FURNACE||b==Blocks.SMOKER) return 3;
        if(b==Blocks.HOPPER||b==Blocks.BREWING_STAND) return 5;
        if(b==Blocks.DISPENSER||b==Blocks.DROPPER) return 9;
        return 0;
    }
    public static boolean hasStoragePalette(fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer container){
        var palette=container.getPalette();
        for(int n=0;n<palette.getPaletteSize();n++){var state=palette.getBlockState(n);if(state!=null&&size(state)>0)return true;}
        return false;
    }
    public static ContainerMenuAdapter create(AbstractContainerMenu menu,BlockPos pos,BlockState state) {
        // InventoryMenu has no MenuType; custom menus may also deliberately reject getType().
        // Accept exact vanilla implementations before asking for their registered type.
        if(!supportedMenu(menu))return null;
        int count=state.is(Blocks.ENDER_CHEST)?27:size(state); if(count==0) return null;
        MenuType<?> type=menu.getType(); Block b=state.getBlock();
        boolean dbl=b instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE;
        MenuType<?> expected=b==Blocks.FURNACE?MenuType.FURNACE:b==Blocks.BLAST_FURNACE?MenuType.BLAST_FURNACE:b==Blocks.SMOKER?MenuType.SMOKER:b==Blocks.HOPPER?MenuType.HOPPER:b==Blocks.BREWING_STAND?MenuType.BREWING_STAND:b==Blocks.DISPENSER||b==Blocks.DROPPER?MenuType.GENERIC_3x3:b instanceof ShulkerBoxBlock?MenuType.SHULKER_BOX:dbl?MenuType.GENERIC_9x6:MenuType.GENERIC_9x3;
        if(type!=expected) return null;
        List<Slot> slots=menu.slots.stream().filter(s->!(s.container instanceof Inventory)).toList();
        if(slots.size()!=count*(dbl?2:1)) return null;
        // All supported vanilla menus use one backing container in logical order.
        for(int i=0;i<slots.size();i++) if(slots.get(i).getContainerSlot()!=i || slots.get(i).container!=slots.get(0).container) return null;
        if(!dbl) return new ContainerMenuAdapter(slots,List.of(pos.immutable()),count);
        BlockPos other=ChestBlock.getConnectedBlockPos(pos,state);
        BlockPos first=state.getValue(ChestBlock.TYPE)==ChestType.RIGHT?pos:other;
        return new ContainerMenuAdapter(slots,List.of(first.immutable(),(first.equals(pos)?other:pos).immutable()),27);
    }
    public static boolean supportedMenu(AbstractContainerMenu menu) {
        Class<?> type=menu.getClass();
        return type==ChestMenu.class||type==FurnaceMenu.class||type==BlastFurnaceMenu.class||type==SmokerMenu.class||type==HopperMenu.class||type==BrewingStandMenu.class||type==DispenserMenu.class||type==ShulkerBoxMenu.class;
    }
    /** Lit/enabled/bottle properties describe contents or redstone, not storage identity. */
    public static String identityState(BlockState state) {
        String block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return state.getBlock() instanceof ChestBlock?block+"/"+state.getValue(ChestBlock.TYPE)+"/"+state.getValue(ChestBlock.FACING):block;
    }
    public static boolean matchesSnapshotState(String stored,BlockState current){
        return normalizeTransient(stored,current).equals(normalizeTransient(current.toString(),current));
    }
    private static String normalizeTransient(String text,BlockState current){
        String[] names=current.getBlock() instanceof AbstractFurnaceBlock?new String[]{"lit"}:current.is(Blocks.BARREL)?new String[]{"open"}:current.is(Blocks.BREWING_STAND)?new String[]{"has_bottle_0","has_bottle_1","has_bottle_2"}:current.is(Blocks.HOPPER)?new String[]{"enabled"}:current.is(Blocks.DISPENSER)||current.is(Blocks.DROPPER)?new String[]{"triggered"}:new String[0];
        for(String name:names)text=text.replace(name+"=true",name+"=false");return text;
    }
    public List<ItemStack> copyItems() { return slots.stream().map(s->s.getItem().copy()).toList(); }
    public boolean matchesItems(List<ItemStack> saved){if(saved==null||saved.size()!=slots.size())return false;for(int n=0;n<slots.size();n++)if(!ItemStack.matches(saved.get(n),slots.get(n).getItem()))return false;return true;}
    public int logical(Slot slot) {int i=slot.getContainerSlot();return i>=0&&i<slots.size()&&slots.get(i)==slot?i:-1;}
}
