package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.StorageOverlayRenderer;
import dev.asaul.litematicainventories.TrackingButton;
import dev.asaul.litematicainventories.ContainerMenuAdapter;
import dev.asaul.litematicainventories.ContainerNameField;
import dev.asaul.litematicainventories.StorageSettings;
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
    @Unique private ContainerNameField storage$name;
    @Inject(method="init",at=@At("RETURN"))
    private void storage$trackingButton(CallbackInfo ci){
        if(storage$tracking!=null){storage$tracking.finishDrag();removeWidget(storage$tracking);}
        var screen=(AbstractContainerScreen<?>)(Object)this;
        if(storage$name!=null){storage$name.finish(true);removeWidget(storage$name);}
        if(ContainerMenuAdapter.supportedMenu(screen.getMenu())){
            storage$tracking=addRenderableWidget(new TrackingButton(screen,leftPos,topPos,imageWidth));
            storage$name=addRenderableWidget(new ContainerNameField(screen.getMenu(),leftPos+8,topPos+6,imageWidth-48));
        }
    }
    @Inject(method="extractContents",at=@At("HEAD"))
    private void storage$renameVisibility(GuiGraphicsExtractor g,int mx,int my,float dt,CallbackInfo ci){if(storage$name!=null){
        storage$name.synchronize(title);int start=leftPos+8,end=leftPos+imageWidth-40;
        if(storage$tracking!=null&&StorageSettings.INSTANCE.showTrackingButton&&storage$tracking.getY()<topPos+18&&storage$tracking.getY()+storage$tracking.getHeight()>topPos+2&&storage$tracking.getX()<end&&storage$tracking.getX()+storage$tracking.getWidth()>start){
            int left=storage$tracking.getX()-4,right=storage$tracking.getX()+storage$tracking.getWidth()+4;if(left-start>=end-right)end=Math.max(start+12,left);else start=Math.min(end-12,right);
        }
        storage$name.setX(start);storage$name.setWidth(Math.max(12,end-start));
    }}
    @ModifyArg(method="extractLabels",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",ordinal=0),index=1)
    private Component storage$localTitle(Component original){return storage$name!=null&&storage$name.visible?Component.empty():original;}
    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void storage$renameKey(net.minecraft.client.input.KeyEvent event,CallbackInfoReturnable<Boolean> ci){if(storage$name!=null&&storage$name.visible&&storage$name.isFocused()){storage$name.keyPressed(event);ci.setReturnValue(true);}}
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void storages$click(MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> ci){
        if(storage$name!=null&&storage$name.visible){
            if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&storage$name.isMouseOver(event.x(),event.y())){storage$name.begin();storage$name.mouseClicked(event,doubleClick);setFocused(storage$name);ci.setReturnValue(true);return;}
            if(storage$name.isFocused())storage$name.finish(true);
        }
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
    private void storage$close(CallbackInfo ci){if(storage$name!=null)storage$name.finish(true);storage$buttonPressed=false;if(storage$tracking!=null)storage$tracking.finishDrag();}
    @Shadow protected Slot hoveredSlot;
    @Inject(method="mouseScrolled",at=@At("HEAD"),cancellable=true)
    private void storage$nestedPage(double x,double y,double horizontal,double vertical,CallbackInfoReturnable<Boolean> ci){if(StorageOverlayRenderer.scrollNested(hoveredSlot,vertical))ci.setReturnValue(true);}
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
