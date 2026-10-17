package dev.asaul.litematicainventories;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.*;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class StorageMaterialScreen extends GuiBase {
    private final Screen parent;private final StorageMaterialList model;
    private String query="";private int sort=0;private boolean missingOnly;
    private final GuiScrollBar scrollbar=new GuiScrollBar();
    private int tableY;
    private final List<ButtonGeneric> headers=new ArrayList<>();
    private List<StorageMaterialList.Row> visible=List.of();
    private List<StorageMaterialList.OfflineRow> offline=List.of();
    public StorageMaterialScreen(Screen p,StorageMaterialList m) {super();parent=p;model=m;setParent(p);title=Component.translatable("storage.tab").getString();useTitleHierarchy=false;}
    private int headerY(){return tableY;}
    private int perPage() {return Math.max(1,(height-headerY()-58)/22);}
    private int colWidth(){return Math.clamp((width-200)/4,44,70);}
    private int itemWidth(){return Math.max(90,Math.min(180,width-4*colWidth()-28));}
    private int rowCount(){return model.rawOffline?offline.size():visible.size();}
    private void filter() {
        if(model.rawOffline){offline=model.offlineRows.stream().filter(r->r.id().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(sort==1?Comparator.comparingLong(StorageMaterialList.OfflineRow::expected).reversed():Comparator.comparing(StorageMaterialList.OfflineRow::id)).toList();scrollbar.setMaxValue(Math.max(0,rowCount()-perPage()));return;}
        var comparator=switch(sort) {case 1->Comparator.comparingLong(StorageMaterialList.Row::expected).reversed();case 2->Comparator.comparingLong(StorageMaterialList.Row::missing).reversed();case 3->Comparator.comparingLong(StorageMaterialList.Row::actual).reversed();case 4->Comparator.comparingLong(StorageMaterialList.Row::extra).reversed();default->Comparator.comparing((StorageMaterialList.Row r)->r.key().stack().getHoverName().getString());};
        visible=model.rows.stream().filter(r->r.key().stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).filter(r->!missingOnly||r.missing()>0).sorted(comparator.thenComparingInt(r->r.key().hashCode())).toList();scrollbar.setMaxValue(Math.max(0,rowCount()-perPage()));
    }
    private ButtonGeneric button(int x,int y,int w,String key,Runnable action) {
        var button=new ButtonGeneric(x,y,w,20,Component.translatable(key).getString());addButton(button,(b,m)->action.run());return button;
    }
    @Override public void initGui() {
        super.initGui();headers.clear();model.refresh();
        int x=12,y=24;
        String[] controls={"storage.refresh","storage.filter."+missingOnly};
        for(int n=0;n<controls.length;n++) {
            int w=font.width(Component.translatable(controls[n]))+10;
            if(x+w>width-12){x=12;y+=22;}
            final int action=n;
            var control=button(x,y,w,controls[n],()->{
                if(action==0){model.refresh();filter();}
                else {missingOnly=!missingOnly;scrollbar.setValue(0);initGui();}
            });if(action==1)control.setEnabled(model.bound&&!model.rawOffline);x+=w+2;
        }
        int searchY=y;
        int searchX=x+6;
        if(width-searchX<180){searchX=12;searchY+=22;}
        var search=new GuiTextFieldGeneric(searchX,searchY+2,width-searchX-12,16,font);
        search.setHint(Component.translatable("storage.search"));search.setValueWrapper(query);
        addTextField(search,field->{query=field.getValueWrapper();scrollbar.setValue(0);filter();return true;});
        tableY=searchY+24;
        filter();
        String[] keys={"storage.item.short","storage.required","storage.header.missing","storage.confirmed","storage.extra"};
        for(int col=0;col<5;col++) {
            final int selected=col;
            var header=new ColumnButton(col==0?12:itemWidth()+(col-1)*colWidth(),tableY,col==0?itemWidth()-14:colWidth()-2);
            addButton(header,(b,m)->{sort=selected;scrollbar.setValue(0);filter();updateHeaders();});
            headers.add(header);
            if(col>=2&&(!model.bound||model.rawOffline))header.setEnabled(false);
        }
        updateHeaders();
        int backWidth=font.width(Component.translatable("storage.building"))+14;
        button(width-backWidth-12,height-30,backWidth,"storage.building",this::onClose);
    }
    private void updateHeaders(){
        String[] keys={"storage.item.short","storage.required","storage.header.missing","storage.confirmed","storage.extra"};
        for(int col=0;col<headers.size();col++)headers.get(col).setDisplayString("§l"+Component.translatable(keys[col]).getString()+(sort==col?" ▾":""));
    }
    private static final class ColumnButton extends ButtonGeneric {
        ColumnButton(int x,int y,int w){super(x,y,w,20,"");}
        @Override public void render(GuiContext g,int mx,int my,boolean selected){
            hovered=isMouseOver(mx,my);
            g.fill(x,y,x+width,y+height,hovered&&enabled?0xA0404040:0xA0101010);
            int border=hovered&&enabled?0xFFAAAAAA:0xFF555555;
            g.fill(x,y,x+width,y+1,border);g.fill(x,y+height-1,x+width,y+height,border);
            g.fill(x,y,x+1,y+height,border);g.fill(x+width-1,y,x+width,y+height,border);
            String text=displayString;
            if(textRenderer.width(text)>width-8)text=textRenderer.plainSubstrByWidth(text,width-14)+"…";
            g.text(textRenderer,text,x+4,y+6,enabled?0xFFFFFFFF:0xFFAAAAAA,false);
        }
    }
    @Override protected void drawContents(GuiContext g,int mx,int my,float a) {
        int colWidth=colWidth(),itemWidth=itemWidth();
        scrollbar.render(g,mx,my,a,width-17,headerY(),7,22+perPage()*22,rowCount()*22+22);
        int start=scrollbar.getValue();
        for(int n=0;n<perPage()&&start+n<rowCount();n++) {
            int y=headerY()+23+n*22;
            if(model.rawOffline){var row=offline.get(start+n);g.fill(10,y-1,width-20,y+21,my>=y&&my<y+22?0xA0707070:n%2==0?0xA0303030:0xA0101010);g.text(font,font.plainSubstrByWidth(row.id(),itemWidth-14),8,y+7,0xFFFFFFFF);g.text(font,Long.toString(row.expected()),itemWidth+3,y+7,0xFFFFFFFF);for(int col=1;col<4;col++)g.text(font,"—",itemWidth+col*colWidth,y+7,0xFF999999);
                if(mx<itemWidth&&my>=y&&my<y+20){var raw=row.key().identity();var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:font.split(Component.literal(raw.toString()),Math.max(100,width-40)))lines.add(line);g.setTooltipForNextFrame(lines,mx,my);}continue;}
            var row=visible.get(start+n);ItemStack stack=row.key().stack();
            g.fill(10,y-1,width-20,y+21,my>=y&&my<y+22?0xA0707070:n%2==0?0xA0303030:0xA0101010);g.fill(12,y+2,28,y+18,0x20FFFFFF);g.item(stack,12,y+2);
            g.text(font,font.plainSubstrByWidth(stack.getHoverName().getString(),itemWidth-28),32,y+7,0xFFFFFFFF);
            String partial=model.actualUnknown>0||model.expectedUnknown>0?"≥":"";
            String[] nums={(model.expectedUnknown>0?"≥":"")+row.expected(),model.bound?partial+row.missing():"—",model.bound?partial+row.actual():"—",model.bound?partial+row.extra():"—"};
            for(int col=0;col<4;col++)g.text(font,nums[col],itemWidth+col*colWidth+3,y+7,col==1?(row.missing()>0?0xFFFF5555:0xFF55FF55):col==3&&row.extra()>0?0xFFFF77BB:0xFFFFFFFF);
            if(mx>=8&&mx<itemWidth&&my>=y&&my<y+20)g.setTooltipForNextFrame(font,stack,mx,my);
        }

    }
    @Override public boolean onMouseScrolled(double x,double y,double horizontal,double vertical){
        if(y>=headerY()&&y<height-36){scrollbar.offsetValue(-(int)vertical*3);return true;}
        return super.onMouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean onMouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&scrollbar.getMaxValue()>0&&scrollbar.wasMouseOver()){scrollbar.setIsDragging(true);return true;}
        return super.onMouseClicked(event,doubleClick);
    }
    @Override public boolean onMouseReleased(MouseButtonEvent event){scrollbar.setIsDragging(false);return super.onMouseReleased(event);}
    @Override public void onClose(){minecraft.setScreenAndShow(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
