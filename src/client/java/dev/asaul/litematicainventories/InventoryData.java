package dev.asaul.litematicainventories;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import java.util.*;

public final class InventoryData {
    public static final String OBSERVATION = "litematicainventories:observation";
    public static CompoundTag encode(List<ItemStack> stacks, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items=NonNullList.withSize(stacks.size(),ItemStack.EMPTY);
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        for(int i=0;i<items.size();i++) {
            ItemStack stack=stacks.get(i);
            if(!stack.isEmpty()) ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
            items.set(i,stack.copy());
        }
        TagValueOutput out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);
        ContainerHelper.saveAllItems(out,items,true);
        CompoundTag tag=out.buildResult();
        long nonempty=stacks.stream().filter(s->!s.isEmpty()).count();
        if (!tag.contains("Items")) {if(nonempty>0)throw new IllegalArgumentException("Inventory serialization omitted nonempty items");tag.put("Items",new ListTag());}
        if(tag.getListOrEmpty("Items").size()!=nonempty)throw new IllegalArgumentException("Incomplete inventory serialization");
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
