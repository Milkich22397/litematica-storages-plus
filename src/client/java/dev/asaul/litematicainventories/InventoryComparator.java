package dev.asaul.litematicainventories;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class InventoryComparator {
    public static final class Key {
        private final ItemStack stack;
        public Key(ItemStack s) { stack=s.copyWithCount(1); }
        public ItemStack stack() { return stack.copy(); }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
        @Override public boolean equals(Object o) {return o instanceof Key k && ItemStack.isSameItemSameComponents(stack,k.stack);}
    }
    public static ComparisonEngine.Amount<Key> amount(ItemStack s) { return new ComparisonEngine.Amount<>(s.isEmpty()?null:new Key(s),s.getCount()); }
    public static ComparisonEngine.Status strict(ItemStack e,ItemStack a) {return ComparisonEngine.strict(amount(e),amount(a));}
    public static Map<Key,ComparisonEngine.Totals> aggregate(List<ItemStack> e,List<ItemStack> a) {return ComparisonEngine.aggregate(e.stream().map(InventoryComparator::amount).toList(),a.stream().map(InventoryComparator::amount).toList());}
}
