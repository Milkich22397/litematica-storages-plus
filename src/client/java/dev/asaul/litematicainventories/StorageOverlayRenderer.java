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
    private static ComparisonEngine.Status[] statuses=new ComparisonEngine.Status[0];
    private static int preparedTick=-1;
    private static boolean preparedSync;
    private static int nestedPage,lastNestedSlot=-1;
    private static final Map<Integer,NestedContents.Result> nested=new HashMap<>();
    public static void reset(){nestedPage=0;lastNestedSlot=-1;nested.clear();opening=null;resolution=null;expected=List.of();actual=List.of();statuses=new ComparisonEngine.Status[0];preparedTick=-1;refreshed=0;}
    private static void update() {
        var o=ContainerRecorder.active();long now=System.currentTimeMillis();
        boolean resolve=o!=opening||now-refreshed>=500;
        if(resolve){
            opening=o;refreshed=now;expected=List.of();actual=List.of();statuses=new ComparisonEngine.Status[0];
            if(o==null){resolution=null;return;}
            resolution=SchematicInventoryResolver.resolve(o);
            if(resolution.placement()==null)return;
            var list=new ArrayList<ItemStack>();
            for(var half:resolution.halves()){boolean known=half!=null&&half.known()&&!half.conflict();list.addAll(known?half.items():Collections.nCopies(o.adapter.halfSize(),ItemStack.EMPTY));}
            expected=list;
        }
        if(o==null||resolution==null||resolution.placement()==null)return;
        int tick=Minecraft.getInstance().player==null?0:Minecraft.getInstance().player.tickCount;
        boolean sync=o.synchronizedMenu;
        if(!resolve&&tick==preparedTick&&sync==preparedSync)return;
        preparedTick=tick;preparedSync=sync;
        nested.clear();actual=o.adapter.copyItems();statuses=new ComparisonEngine.Status[expected.size()];
        for(int i=0;i<statuses.length;i++){
            var half=resolution.halves().get(i/o.adapter.halfSize());var state=ComparisonEngine.Status.UNKNOWN;
            if(sync&&half!=null&&half.known()&&!half.conflict()){
                state=InventoryComparator.strict(expected.get(i),actual.get(i));
            }
            statuses[i]=state;
        }
    }
    private static ComparisonEngine.Status status(int i){return i>=0&&i<statuses.length?statuses[i]:ComparisonEngine.Status.UNKNOWN;}
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
        if(!StorageSettings.INSTANCE.overlay)return List.of();update();if(opening==null||resolution==null||resolution.placement()==null)return List.of();
        int i=opening.adapter.logical(slot);if(i<0||i>=expected.size())return List.of();
        var e=resolution.halves().get(i/opening.adapter.halfSize());
        if(e==null||!e.known()||e.conflict())return List.of(Component.translatable("storage.status."+(e!=null&&e.conflict()?"conflict":"unknown")));
        var state=status(i);
        if(state==ComparisonEngine.Status.MATCH)return List.of();
        if(state==ComparisonEngine.Status.UNKNOWN)return List.of(Component.translatable("storage.status.sync"));
        var want=expected.get(i);var have=actual.get(i);
        var lines=new ArrayList<Component>(switch(state) {
            case MISSING,WRONG->List.of(Component.translatable("storage.expected",want.getHoverName().getString(),want.getCount()));
            case UNDER,OVER->List.of(Component.translatable("storage.hover.count",want.getCount()),
                Component.translatable(state==ComparisonEngine.Status.UNDER?"storage.hover.missing":"storage.hover.extra",Math.abs(want.getCount()-have.getCount())));
            case EXTRA->List.of(Component.translatable("storage.hover.extra",have.getCount()));
            default->List.of();
        });addNested(lines,i,want,have);return lines;
    }
    public static boolean scrollNested(Slot slot,double amount){
        if(!StorageSettings.INSTANCE.overlay||slot==null||amount==0)return false;
        boolean shift=com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)||com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
        if(!shift)return false;update();if(opening==null)return false;int i=opening.adapter.logical(slot);if(i<0||i>=expected.size()||i>=actual.size())return false;
        var result=nested.computeIfAbsent(i,k->NestedContents.compare(expected.get(i),actual.get(i)));if(result.differences().size()<=8)return false;
        if(lastNestedSlot!=i){lastNestedSlot=i;nestedPage=0;}nestedPage=Math.clamp(nestedPage+(amount<0?1:-1),0,(result.differences().size()-1)/8);return true;
    }
    private static void addNested(List<Component> lines,int slot,ItemStack expected,ItemStack actual){
        if(expected.isEmpty())return;
        if(lastNestedSlot!=slot){lastNestedSlot=slot;nestedPage=0;}
        var comparison=nested.computeIfAbsent(slot,k->NestedContents.compare(expected,actual));

        boolean shift=com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)||com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
        lines.addAll(comparison.tooltip(shift,nestedPage));
    }
}
