package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Coordinates are GUI units relative to the container's top-right corner, per menu class. */
public final class TrackingButton extends AbstractWidget {
    private static final Identifier ICON=Identifier.fromNamespaceAndPath("litematicainventories","textures/gui/tracking.png");
    private final AbstractContainerScreen<?> screen;
    private final int left,top,imageWidth;
    private final String layout;
    private boolean dragging;
    private boolean pressed;
    private long pressedAt;
    private double pressX,pressY;
    private double grabX,grabY;
    public TrackingButton(AbstractContainerScreen<?> screen,int left,int top,int imageWidth){
        super(0,0,16,14,Component.translatable("storages.track.add"));this.screen=screen;this.left=left;this.top=top;this.imageWidth=imageWidth;layout=screen.getMenu().getClass().getName()+".header-v2";position();
    }
    private ContainerRecorder.Opening opening(){var o=ContainerRecorder.active();return o!=null&&o.menu==screen.getMenu()?o:null;}
    private boolean shown(){return StorageSettings.INSTANCE.showTrackingButton&&opening()!=null;}
    private void position(){
        var saved=StorageSettings.INSTANCE.trackingPositions.get(layout);int x,y;
        if(saved!=null){x=left+imageWidth+saved.x();y=top+saved.y();}
        else {x=left+imageWidth-34;y=top+3;}
        setX(Math.clamp(x,0,Math.max(0,screen.width-width)));setY(Math.clamp(y,0,Math.max(0,screen.height-height)));
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        if(!shown())return;if(pressed&&System.nanoTime()-pressedAt>=350_000_000L)dragging=true;if(!dragging)position();var o=opening();active=true;
        boolean tracked=TrackedStorageStore.tracked(o),edit=StorageSettings.arrangeTrackingButton;
        int x=getX(),y=getY();
        g.blit(RenderPipelines.GUI_TEXTURED,ICON,x,y,0f,0f,16,14,1328,1184,1328,1184);
        // A small status mark leaves the PNG background completely transparent.
        if(tracked)g.fill(x+12,y+10,x+15,y+13,0xFF55DD77);
        var text=Component.translatable(edit?"storages.track.drag":!StorageSettings.INSTANCE.resourceTracking?"storages.disabled":!o.synchronizedMenu?"storage.status.sync":tracked?"storages.track.remove":"storages.track.add");
        setMessage(text);if(isHovered&&!pressed)g.setTooltipForNextFrame(Minecraft.getInstance().font,text.copy().append("\n").append(Component.translatable("storages.track.hold")),mx,my);
    }
    @Override public boolean isMouseOver(double x,double y){return shown()&&super.isMouseOver(x,y);}
    @Override public void onClick(MouseButtonEvent event,boolean doubleClick){
        pressed=true;pressedAt=System.nanoTime();pressX=event.x();pressY=event.y();grabX=event.x()-getX();grabY=event.y()-getY();dragging=StorageSettings.arrangeTrackingButton;
    }
    public boolean dragging(){return dragging;}
    public boolean pressed(){return pressed;}
    public void drag(MouseButtonEvent event){if(!pressed)return;if(Math.hypot(event.x()-pressX,event.y()-pressY)>=4||System.nanoTime()-pressedAt>=350_000_000L)dragging=true;if(!dragging)return;setX(Math.clamp((int)(event.x()-grabX),0,Math.max(0,screen.width-width)));setY(Math.clamp((int)(event.y()-grabY),0,Math.max(0,screen.height-height)));}
    @Override public void onRelease(MouseButtonEvent event){
        if(!pressed)return;
        boolean moved=dragging||System.nanoTime()-pressedAt>=350_000_000L;
        pressed=false;
        if(moved){dragging=true;finishDrag();}
        else if(isMouseOver(event.x(),event.y())){var o=opening();if(o!=null&&o.synchronizedMenu&&StorageSettings.INSTANCE.resourceTracking)TrackedStorageStore.toggle(o);}
    }
    private boolean coversSlot(int x,int y){return screen.getMenu().slots.stream().anyMatch(s->x<left+s.x+16&&x+width>left+s.x&&y<top+s.y+16&&y+height>top+s.y);}
    public void finishDrag(){
        pressed=false;if(!dragging)return;dragging=false;
        if(coversSlot(getX(),getY())){
            int bestX=getX(),bestY=getY();long distance=Long.MAX_VALUE;
            int[][] candidates={{left-24,getY()},{left+imageWidth+2,getY()},{getX(),top-24},{0,0},{screen.width-22,0},{0,screen.height-22},{screen.width-22,screen.height-22}};
            for(int[] p:candidates)if(p[0]>=0&&p[1]>=0&&p[0]+width<=screen.width&&p[1]+height<=screen.height&&!coversSlot(p[0],p[1])){
                long d=(long)(p[0]-getX())*(p[0]-getX())+(long)(p[1]-getY())*(p[1]-getY());if(d<distance){distance=d;bestX=p[0];bestY=p[1];}
            }
            setX(bestX);setY(bestY);
        }
        StorageSettings.INSTANCE.trackingPositions.put(layout,new StorageSettings.ButtonPosition(getX()-left-imageWidth,getY()-top));StorageSettings.save();
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
}
