package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.mojang.blaze3d.platform.InputConstants;

/** Always present for tracked containers; local label only, with a transparent title-bar field. */
public final class ContainerNameField extends EditBox {
    private final AbstractContainerMenu menu;
    private TrackedStorageStore.Key key;
    private String fallback="";
    private boolean editing;
    public ContainerNameField(AbstractContainerMenu menu,int x,int y,int width){super(Minecraft.getInstance().font,x,y,width,10,Component.translatable("storages.label"));this.menu=menu;setMaxLength(80);setBordered(false);setTextShadow(false);setTextColor(0xFF404040);setTextColorUneditable(0xFF404040);visible=false;}
    private ContainerRecorder.Opening opening(){var o=ContainerRecorder.active();return o!=null&&o.menu==menu&&o.synchronizedMenu?o:null;}
    public boolean available(){var o=opening();return o!=null&&TrackedStorageStore.tracked(o);}
    @Override public void setFocused(boolean focused){super.setFocused(focused);if(focused&&visible&&key!=null)editing=true;}
    private void reload(){var o=opening();var e=o==null?null:TrackedStorageStore.get(o.trackingKey);setValue(e==null||e.label().isBlank()?fallback:e.label());}
    public void synchronize(Component original){fallback=original.getString();boolean show=available();if(!show){if(editing)finish(true);visible=false;setFocused(false);return;}key=opening().trackingKey;if(!visible){reload();visible=true;setFocused(false);}else if(!isFocused()){if(editing)finish(true);String prior=getValue();var o=opening();var e=TrackedStorageStore.get(o.trackingKey);String text=e==null||e.label().isBlank()?fallback:e.label();if(!prior.equals(text))setValue(text);}}
    public void begin(){if(visible&&available()){editing=true;setFocused(true);}}
    public void finish(boolean save){if(save&&editing&&key!=null){var e=TrackedStorageStore.get(key);if(e!=null){String text=getValue().strip();TrackedStorageStore.edit(e.key(),text.equals(fallback)?"":text,e.group());}}editing=false;setFocused(false);var screen=Minecraft.getInstance().gui.screen();if(screen!=null&&screen.getFocused()==this)screen.setFocused(null);if(available())reload();}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==InputConstants.KEY_RETURN||event.key()==InputConstants.KEY_NUMPADENTER){finish(true);return true;}if(event.key()==InputConstants.KEY_ESCAPE){finish(false);return true;}return super.keyPressed(event);}
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){if(!visible)return;int x=getX()-2,y=getY()-2,w=getWidth()+4,h=getHeight()+2;int color=isFocused()?0xFF74889A:0xFF999999;g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);super.extractWidgetRenderState(g,mx,my,dt);}
}
