package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Resolve/deserialize at most twice per second; slot drawing only uses prepared values. */
public final class StorageOverlayRenderer {
    private static ContainerRecorder.Opening opening;
    private static SchematicInventoryResolver.Resolution resolution;
    private static long refreshed;
    private static List<ItemStack> expected=List.of(),actual=List.of();
    private static boolean complete;
    private static Map<InventoryComparator.Key,ComparisonEngine.Totals> totals=Map.of();
    private static void update() {
        var o=ContainerRecorder.active();long now=System.currentTimeMillis();
        if(o==opening && now-refreshed<500) return;
        opening=o; refreshed=now;expected=List.of();actual=List.of();totals=Map.of();complete=false;
        if(o==null) {resolution=null;return;}
        resolution=SchematicInventoryResolver.resolve(o);
        if(resolution.placement()==null)return;
        var list=new ArrayList<ItemStack>(); complete=true;
        for(var half:resolution.halves()) {boolean known=half!=null&&half.known()&&!half.conflict();complete &= known;list.addAll(known?half.items():Collections.nCopies(o.adapter.halfSize(),ItemStack.EMPTY));}
        expected=list; actual=o.adapter.copyItems(); if(complete && o.synchronizedMenu) totals=InventoryComparator.aggregate(expected,actual);
    }
    private static ComparisonEngine.Status status(int i) {
        if(opening==null || !opening.synchronizedMenu || i<0 || i>=expected.size()) return ComparisonEngine.Status.UNKNOWN;
        var e=resolution.halves().get(i/opening.adapter.halfSize());
        if(e==null||!e.known()||e.conflict()) return ComparisonEngine.Status.UNKNOWN;
        if(!StorageSettings.INSTANCE.aggregate) return InventoryComparator.strict(expected.get(i),actual.get(i));
        if(!complete)return ComparisonEngine.Status.UNKNOWN;
        ItemStack a=actual.get(i),want=expected.get(i);
        // The same shortage marker goes to the FIRST expected slot of each key.
        if(!want.isEmpty()) {
            var key=new InventoryComparator.Key(want);var t=totals.get(key);
            boolean first=true;for(int j=0;j<i;j++) if(!expected.get(j).isEmpty()&&key.equals(new InventoryComparator.Key(expected.get(j)))) {first=false;break;}
            if(first&&t.missing()>0) return ComparisonEngine.Status.MISSING;
        }
        if(!a.isEmpty()) { var t=totals.get(new InventoryComparator.Key(a));if(t!=null && t.extra()>0) return ComparisonEngine.Status.EXTRA; }
        return ComparisonEngine.Status.MATCH;
    }
    private static int index(Slot slot) {
        if(!StorageSettings.INSTANCE.overlay)return -1;
        update();if(opening==null||resolution==null||resolution.placement()==null)return -1;
        int i=opening.adapter.logical(slot);return i>=0&&i<expected.size()?i:-1;
    }
    public static void background(GuiGraphicsExtractor g,Slot slot) {
        int i=index(slot);if(i<0)return;
        var cfg=StorageSettings.INSTANCE;
        int color=switch(status(i)) {
            case MISSING->cfg.missingColor;case UNDER,OVER->cfg.countColor;
            case WRONG->cfg.wrongColor;case EXTRA->cfg.extraColor;default->0;
        };
        if(color!=0)g.fill(slot.x,slot.y,slot.x+16,slot.y+16,color);
    }
    public static void slot(GuiGraphicsExtractor g,Slot slot) {
        int i=index(slot);if(i<0||!slot.getItem().isEmpty()||status(i)!=ComparisonEngine.Status.MISSING)return;
        ItemStack want=expected.get(i);if(want.isEmpty())return;
        GhostItems.extracting=true;
        try {g.item(want,slot.x,slot.y);} finally {GhostItems.extracting=false;}
        g.itemDecorations(Minecraft.getInstance().font,want,slot.x,slot.y);
    }
    public static List<Component> tooltip(Slot slot) {
        update();if(!StorageSettings.INSTANCE.overlay||opening==null||resolution==null||resolution.placement()==null)return List.of();
        int i=opening.adapter.logical(slot);if(i<0||i>=expected.size())return List.of();
        var e=resolution.halves().get(i/opening.adapter.halfSize());
        if(e==null||!e.known()||e.conflict())return List.of(Component.translatable("storage.status."+(e!=null&&e.conflict()?"conflict":"unknown")));
        var state=status(i);
        if(state==ComparisonEngine.Status.MATCH)return List.of();
        if(state==ComparisonEngine.Status.UNKNOWN)return List.of(Component.translatable("storage.status.sync"));
        var want=expected.get(i);var have=actual.get(i);
        if(StorageSettings.INSTANCE.aggregate) {
            var item=state==ComparisonEngine.Status.EXTRA?have:want;
            var total=totals.get(new InventoryComparator.Key(item));
            return List.of(Component.translatable("storage.hover.container",total.expected(),total.actual()));
        }
        return switch(state) {
            case MISSING,WRONG->List.of(Component.translatable("storage.expected",want.getHoverName().getString(),want.getCount()));
            case UNDER,OVER->List.of(Component.translatable("storage.hover.count",want.getCount()),
                Component.translatable(state==ComparisonEngine.Status.UNDER?"storage.hover.missing":"storage.hover.extra",Math.abs(want.getCount()-have.getCount())));
            case EXTRA->List.of(Component.translatable("storage.hover.extra",have.getCount()));
            default->List.of();
        };
    }
}
