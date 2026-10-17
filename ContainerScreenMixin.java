package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.StorageOverlayRenderer;
import dev.asaul.litematicainventories.TrackingButton;
import dev.asaul.litematicainventories.ContainerMenuAdapter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
@Mixin(value=AbstractContainerScreen.class,priority=900)
public abstract class ContainerScreenMixin extends Screen {
    protected ContainerScreenMixin(Component title){super(title);}
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected int imageWidth;
    @Unique private TrackingButton storage$tracking;
    @Unique private boolean storage$buttonPressed;
    @Inject(method="init",at=@At("RETURN"))
    private void storage$trackingButton(CallbackInfo ci){
        if(storage$tracking!=null){storage$tracking.finishDrag();removeWidget(storage$tracking);}
        var screen=(AbstractContainerScreen<?>)(Object)this;
        if(ContainerMenuAdapter.supportedMenu(screen.getMenu()))storage$tracking=addRenderableWidget(new TrackingButton(screen,leftPos,topPos,imageWidth));
    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void storages$click(MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> ci){
        // Handle only our own rectangle before Chest Tracker's resize hit testing.
        if(storage$tracking!=null&&storage$tracking.isMouseOver(event.x(),event.y())&&storage$tracking.mouseClicked(event,doubleClick)){
            storage$buttonPressed=true;setFocused(storage$tracking);setDragging(true);ci.setReturnValue(true);
        }
    }
    @Inject(method="mouseDragged",at=@At("HEAD"),cancellable=true)
    private void storage$drag(MouseButtonEvent event,double dx,double dy,CallbackInfoReturnable<Boolean> ci){if(storage$tracking!=null&&storage$tracking.pressed()){storage$tracking.drag(event);ci.setReturnValue(true);}}
    @Inject(method="mouseReleased",at=@At("HEAD"),cancellable=true)
    private void storage$release(MouseButtonEvent event,CallbackInfoReturnable<Boolean> ci){if(storage$tracking!=null&&storage$buttonPressed&&event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){storage$buttonPressed=false;storage$tracking.onRelease(event);setDragging(false);ci.setReturnValue(true);}}
    @Inject(method="removed",at=@At("HEAD"))
    private void storage$close(CallbackInfo ci){storage$buttonPressed=false;if(storage$tracking!=null)storage$tracking.finishDrag();}
    @Shadow protected Slot hoveredSlot;
    @Inject(method="extractSlot",at=@At("HEAD"))
    private void storage$background(GuiGraphicsExtractor g,Slot slot,int mx,int my,CallbackInfo ci) {StorageOverlayRenderer.background(g,slot);}
    @Inject(method="extractSlot",at=@At("RETURN"))
    private void storage$slot(GuiGraphicsExtractor g,Slot slot,int mx,int my,CallbackInfo ci) {StorageOverlayRenderer.slot(g,slot);}
    @Inject(method="getTooltipFromContainerItem",at=@At("RETURN"),cancellable=true)
    private void storage$tooltip(ItemStack item,CallbackInfoReturnable<List<Component>> ci) {
        if(hoveredSlot==null)return;var extra=StorageOverlayRenderer.tooltip(hoveredSlot);if(extra.isEmpty())return;
        var lines=new ArrayList<>(ci.getReturnValue());lines.addAll(extra);ci.setReturnValue(lines);
    }
    @Inject(method="extractTooltip",at=@At("RETURN"))
    private void storage$emptyTooltip(GuiGraphicsExtractor g,int mx,int my,CallbackInfo ci) {
        if(hoveredSlot==null||hoveredSlot.hasItem())return;var lines=StorageOverlayRenderer.tooltip(hoveredSlot);
        if(!lines.isEmpty())g.setTooltipForNextFrame(Minecraft.getInstance().font,lines,Optional.empty(),mx,my);
    }
}
