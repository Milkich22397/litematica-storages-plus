package dev.asaul.litematicainventories;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class InventoryData {
    public static final String OBSERVATION = "litematicainventories:observation";
    public static CompoundTag encode(List<ItemStack> stacks, HolderLookup.Provider registries) {
        var list=new ListTag();
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        for(int i=0;i<stacks.size();i++) {
            ItemStack stack=stacks.get(i);
            if(stack.isEmpty())continue;
            var encoded=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
            if(!(encoded instanceof CompoundTag item))throw new IllegalArgumentException("Invalid encoded stack");
            item.putByte("Slot",(byte)i);list.add(item);
        }
        var tag=new CompoundTag();tag.put("Items",list);
        return tag;
    }
    /** Fail closed on unknown ids/components, duplicate or out-of-range slots. */
    public static List<ItemStack> decode(CompoundTag tag,int size,HolderLookup.Provider registries) {
        if (!(tag.get("Items") instanceof ListTag list)) throw new IllegalArgumentException("Unknown inventory");
        List<ItemStack> items=new ArrayList<>(Collections.nCopies(size,ItemStack.EMPTY)); Set<Integer> seen=new HashSet<>();
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        for(Tag raw:list) {
            if (!(raw instanceof CompoundTag e)) throw new IllegalArgumentException("Invalid stack");
            int slot=e.getIntOr("Slot",-1);
            if(slot<0||slot>=size||!seen.add(slot)) throw new IllegalArgumentException("Invalid slot: "+slot);
            items.set(slot,ItemStack.CODEC.parse(ops,e).getOrThrow());
        }
        return items;
    }
}
