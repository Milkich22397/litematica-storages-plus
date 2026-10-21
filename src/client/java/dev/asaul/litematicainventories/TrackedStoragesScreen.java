package dev.asaul.litematicainventories;

import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.*;

public class TrackedStoragesScreen extends StorageDashboard {
    private String filter=StorageGroups.ALL;
    private TrackedStorageStore.Key selectedStorage;
    private int tableY,scroll,totalHeight;
    private final Set<String> collapsed=new HashSet<>();
    private record Line(String group,TrackedStorageStore.Entry entry,int y,int height){}
    private List<Line> lines=List.of();
    private List<Line> visible=List.of();
    private final Map<String,Integer> groupSizes=new HashMap<>();
    private TrackedStorageStore.Key pressedEntry;
    private boolean dragging;
    private double pressX,pressY,mouseX,mouseY;
    private long pressedAt;
    private List<TrackedStorageStore.Entry> rows=List.of();
    private long rowsRevision=-1,groupsRevision=-1;
    private String rowsServer="",rowsPlayer="",rowsQuery="",rowsFilter="";
    private List<TrackedStorageStore.Entry> layoutRows;
    private record Content(TrackedStorageStore.Entry entry,Map<String,Long> names){}
    private final Map<TrackedStorageStore.Key,Content> contents=new HashMap<>();
    private Map<String,Long> contents(TrackedStorageStore.Entry e){
        var cached=contents.get(e.key());if(cached!=null&&cached.entry()==e)return cached.names();
        var names=new TreeMap<String,Long>();
        if(mc.level!=null)try{
            var items=InventoryData.decode(e.data(),e.size(),mc.level.registryAccess());
            var counts=fi.dy.masa.litematica.materials.MaterialListUtils.getInventoryItemCounts(new net.minecraft.world.SimpleContainer(items.toArray(ItemStack[]::new)));
            counts.object2IntEntrySet().forEach(i->names.merge(i.getKey().getStack().getHoverName().getString(),(long)i.getIntValue(),Long::sum));
        }catch(RuntimeException ignored){} // A malformed observation must not make the manager inaccessible.
        var result=Collections.unmodifiableMap(names);contents.put(e.key(),new Content(e,result));return result;
    }
    private String detail(TrackedStorageStore.Entry e){
        String group=StorageGroups.name(e.key().server(),e.group()),q=query.toLowerCase(Locale.ROOT);
        if(!q.isBlank())for(var item:contents(e).entrySet())if(item.getKey().toLowerCase(Locale.ROOT).contains(q))return group+" \u00b7 "+item.getKey()+" \u00d7"+item.getValue();
        return group;
    }
    public TrackedStoragesScreen(Screen parent,MaterialListBase list){this(parent,list,0,null);}
    protected TrackedStoragesScreen(Screen parent,MaterialListBase list,int initialTab,List<CacheKey> keys){super(parent,list,initialTab,keys);}
    private int viewport(){return Math.max(38,height-tableY-40);}
    private void layout(){
        if(layoutRows==rows){updateVisible();return;}
        var result=new ArrayList<Line>();int y=0;String server=StorageGroups.server();var grouped=rows.stream().collect(java.util.stream.Collectors.groupingBy(TrackedStorageStore.Entry::group));groupSizes.clear();
        for(String id:StorageGroups.choices(server)){
            if(id.equals(StorageGroups.ALL)||!filter.equals(StorageGroups.ALL)&&!filter.equals(id))continue;
            var members=grouped.getOrDefault(id,List.of());groupSizes.put(id,members.size());
            result.add(new Line(id,null,y,22));y+=22;
            if(!collapsed.contains(id))for(var entry:members){result.add(new Line(id,entry,y,38));y+=38;}
        }
        lines=List.copyOf(result);layoutRows=rows;totalHeight=y;updateVisible();
    }
    private void updateVisible(){
        scroll=Math.clamp(scroll,0,Math.max(0,totalHeight-viewport()));
        int low=0,high=lines.size();
        while(low<high){int mid=(low+high)>>>1;if(lines.get(mid).y()<scroll)low=mid+1;else high=mid;}
        int end=low;while(end<lines.size()&&lines.get(end).y()+lines.get(end).height()<=scroll+viewport())end++;
        visible=lines.subList(low,end);
    }
    private List<Line> visibleLines(){return visible;}
    private Line hit(double x,double y){if(x<8||x>leftRight-8||y<tableY||y>=tableY+viewport())return null;for(var l:visibleLines())if(y>=tableY+l.y()-scroll&&y<tableY+l.y()-scroll+l.height())return l;return null;}
    private void move(TrackedStorageStore.Key key,String group){var e=TrackedStorageStore.get(key);if(e!=null){TrackedStorageStore.edit(key,e.label(),group);ResourceAccounting.tick(mc);}}

    private ButtonGeneric button(int x,int y,int w,String text,Runnable action){var b=new ButtonGeneric(x,y,w,20,font.plainSubstrByWidth(text,Math.max(10,w-8)));addButton(b,(a,m)->action.run());return b;}
    public static ItemStack icon(TrackedStorageStore.Entry e){if(e.personal())return new ItemStack(Items.ENDER_CHEST);String id=e.states().isEmpty()?"minecraft:chest":e.states().getFirst().split("/",2)[0];var parsed=Identifier.tryParse(id);return parsed==null?new ItemStack(Items.CHEST):new ItemStack(BuiltInRegistries.BLOCK.getValue(parsed).asItem());}
    public static String name(TrackedStorageStore.Entry e){return e.label().isBlank()?icon(e).getHoverName().getString():e.label();}
    public static String coordinates(TrackedStorageStore.Entry e){if(e.personal())return tr("storages.manager.personal");var coords=e.positions().stream().map(p->p.getX()+", "+p.getY()+", "+p.getZ()).reduce((a,b)->a+" / "+b).orElse("");return coords+" \u00b7 "+e.key().dimension().replace("minecraft:","");}
    private void refresh(){
        String world=StorageGroups.server(),q=query.toLowerCase(Locale.ROOT);var player=mc.player;
        String playerId=player==null?"":player.getUUID().toString();
        long revision=TrackedStorageStore.revision(),organization=StorageGroups.revision();
        if(rowsRevision==revision&&groupsRevision==organization&&rowsServer.equals(world)&&rowsPlayer.equals(playerId)&&rowsQuery.equals(q)&&rowsFilter.equals(filter)){layout();return;}
        rowsRevision=revision;groupsRevision=organization;rowsServer=world;rowsPlayer=playerId;rowsQuery=q;rowsFilter=filter;
        layoutRows=null;
        var snapshot=TrackedStorageStore.snapshot();var retained=new HashSet<TrackedStorageStore.Key>();for(var e:snapshot)retained.add(e.key());contents.keySet().retainAll(retained);
        rows=snapshot.stream().filter(e->e.key().server().equals(world)&&(!e.personal()||player!=null&&e.key().identity().equals(playerId)))
            .filter(e->filter.equals(StorageGroups.ALL)||filter.equals(e.group()))
            .filter(e->(name(e)+" "+coordinates(e)+" "+StorageGroups.name(world,e.group())).toLowerCase(Locale.ROOT).contains(q)||contents(e).keySet().stream().anyMatch(n->n.toLowerCase(Locale.ROOT).contains(q)))
            .sorted(Comparator.comparing(TrackedStoragesScreen::name).thenComparing(e->e.key().identity())).toList();layout();
    }
    private void selectStorage(TrackedStorageStore.Entry e){
        selectedStorage=e.key();StringBuilder body=new StringBuilder(coordinates(e)).append("\n").append(tr("storages.manager.filter",StorageGroups.name(e.key().server(),e.group()))).append("\n").append(tr(e.valid()?"storages.manager.saved":"storages.manager.invalid"));
        if(e.observedAt()>0)body.append("\n").append(tr("dashboard.observed",Math.max(0,(System.currentTimeMillis()-e.observedAt())/3600000)));
        int count=0;for(var item:contents(e).entrySet()){if(count++==8){body.append("\n...");break;}body.append("\n").append(item.getKey()).append(" x").append(item.getValue());}
        body.append("\n\n").append(tr("storages.groups.drag"));describe("storage:"+e.key().identity(),name(e),body.toString());
    }
    @Override protected void initStorages(){
        String server=StorageGroups.server();if(!StorageGroups.choices(server).contains(filter))filter=StorageGroups.ALL;int w=leftRight-16,col=(w-4)/2;
        String selection=StorageGroups.selection(server,materialList);
        button("filter",8,34,col,tr("storages.manager.filter",StorageGroups.name(server,filter)),()->{var ids=StorageGroups.choices(server);filter=ids.get((ids.indexOf(filter)+1)%ids.size());scroll=0;initGui();});
        button("count",12+col,34,col,tr("storages.manager.count",StorageGroups.name(server,selection)),()->{var ids=StorageGroups.choices(server);String next=ids.get((ids.indexOf(StorageGroups.selection(server,materialList))+1)%ids.size());if(StorageGroups.bound(server,materialList))StorageGroups.bind(server,materialList,next);else StorageGroups.select(server,next);ResourceAccounting.tick(mc);initGui();}).setHoverStrings(tr("storages.manager.count.help"));
        button("storages.groups.manage",8,58,col,tr("storages.groups.manage"),()->mc.setScreenAndShow(new StorageGroupsScreen(this)));
        var bind=button("binding",12+col,58,col,tr(StorageGroups.bound(server,materialList)?"storages.groups.unbind":"storages.groups.bind"),()->{if(StorageGroups.bound(server,materialList))StorageGroups.unbind(server,materialList);else StorageGroups.bind(server,materialList,StorageGroups.selection(server,materialList));initGui();});bind.setEnabled(StorageGroups.schematicId(materialList)!=null);bind.setHoverStrings(StorageGroups.schematicId(materialList)==null?tr("storages.groups.save_first"):tr("storages.groups.for",materialList.getName()));
        tableY=84;refresh();
        var chosen=rows.stream().filter(e->e.key().equals(selectedStorage)).findFirst().orElse(rows.isEmpty()?null:rows.getFirst());
        if(chosen!=null)selectStorage(chosen);else {selectedStorage=null;describe("storage:none",tr("dashboard.info.title"),tr("dashboard.info.storage"));}
        int actionW=(railWidth-16)/3;
        var locate=button("storages.locate",railX+4,34,actionW,tr("storages.locate"),()->{var e=TrackedStorageStore.get(selectedStorage);if(e!=null&&StorageLocator.show(e)){StorageSettings.save();mc.setScreenAndShow(null);}});locate.setEnabled(chosen!=null&&StorageLocator.canShow(chosen));
        button("storages.edit",railX+8+actionW,34,actionW,tr("storages.edit"),()->{if(selectedStorage!=null)mc.setScreenAndShow(new StorageEntryScreen(this,selectedStorage));}).setEnabled(chosen!=null);
        button("storages.remove",railX+12+2*actionW,34,actionW,tr("storages.remove"),()->{var e=TrackedStorageStore.get(selectedStorage);if(e!=null)mc.setScreenAndShow(new ConfirmScreen(yes->{if(yes)TrackedStorageStore.remove(e.key());mc.setScreenAndShow(this);},Component.translatable("storages.remove"),Component.literal(name(e)+" / "+coordinates(e))));}).setEnabled(chosen!=null);
        button("scroll.up",8,height-28,26,"<",()->{scroll=Math.max(0,scroll-viewport());initGui();});button("scroll.down",38,height-28,26,">",()->{scroll=Math.min(Math.max(0,totalHeight-viewport()),scroll+viewport());initGui();});
        button("storages.locate.clear",72,height-28,Math.max(40,leftRight-80),tr("storages.locate.clear"),StorageLocator::clear);
    }
    @Override protected void drawStorageBackground(GuiContext g,int mx,int my){
        var over=hit(mx,my);
        for(var l:visibleLines()){int y=tableY+l.y()-scroll;
            g.fill(8,y,leftRight-8,y+l.height()-1,l.entry()==null?0xB0202020:l.entry().key().equals(selectedStorage)?0xA0405068:0x80303030);
            if(dragging&&over!=null&&over.group().equals(l.group()))g.fill(8,y,leftRight-8,y+l.height()-1,0x505599FF);
        }
    }
    @Override protected void drawStorages(GuiContext g,int mx,int my,float dt){
        int textWidth=Math.max(55,leftRight-50);var hovered=hit(mx,my);
        for(var l:visibleLines()){
            int y=tableY+l.y()-scroll;var e=l.entry();
            if(e==null){String label=(collapsed.contains(l.group())?"\u25B6 ":"\u25BC ")+StorageGroups.name(StorageGroups.server(),l.group())+" ("+groupSizes.getOrDefault(l.group(),0)+")";label=font.plainSubstrByWidth(label,leftRight-28);g.text(font,label,14,y+6,0xFFDDDDDD);int end=20+font.width(label);if(end<leftRight-14)g.fill(end,y+11,leftRight-14,y+12,0xFF777777);continue;}
            g.item(icon(e),13,y+3);
            g.text(font,font.plainSubstrByWidth(name(e),textWidth),33,y+3,e.valid()?0xFFFFFFFF:0xFF999999);
            g.text(font,font.plainSubstrByWidth(detail(e),textWidth),33,y+14,0xFFAAAAAA);
            g.text(font,font.plainSubstrByWidth(coordinates(e),leftRight-26),13,y+26,0xFFCCCCCC);
            if(!dragging&&mx<leftRight&&hovered==l)g.getGuiGraphics().setTooltipForNextFrame(font,List.of(Component.literal(name(e)),Component.literal(coordinates(e)),Component.literal(StorageGroups.name(e.key().server(),e.group())),Component.translatable(e.valid()?"storages.manager.saved":"storages.manager.invalid"),Component.translatable("storages.groups.drag")),Optional.empty(),mx,my);
        }
        if(dragging&&pressedEntry!=null&&mx<leftRight-20){var e=TrackedStorageStore.get(pressedEntry);if(e!=null){g.fill(mx+8,my+8,Math.min(leftRight,mx+font.width(name(e))+22),my+24,0xDD101010);g.text(font,font.plainSubstrByWidth(name(e),Math.max(20,leftRight-mx-18)),mx+12,my+12,0xFFFFFFFF);}}
        if(rows.isEmpty()&&!query.isBlank())g.text(font,tr("storages.manager.empty"),12,tableY+26,0xFFAAAAAA);
    }
    @Override protected boolean storageClick(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick){
        if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){var l=hit(event.x(),event.y());
            if(l!=null&&l.entry()==null){if(!collapsed.add(l.group()))collapsed.remove(l.group());layoutRows=null;initGui();return true;}
            if(l!=null){selectStorage(l.entry());pressedEntry=l.entry().key();pressX=mouseX=event.x();pressY=mouseY=event.y();pressedAt=System.nanoTime();dragging=false;return true;}
        }return false;
    }
    @Override protected boolean storageDrag(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){
        if(pressedEntry==null)return false;mouseX=event.x();mouseY=event.y();if(Math.hypot(mouseX-pressX,mouseY-pressY)>=4||System.nanoTime()-pressedAt>=350_000_000L)dragging=true;return true;
    }
    @Override protected boolean storageRelease(net.minecraft.client.input.MouseButtonEvent event){
        if(pressedEntry!=null&&event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){var l=hit(event.x(),event.y());if(dragging&&l!=null)move(pressedEntry,l.group());pressedEntry=null;dragging=false;initGui();return true;}return false;
    }
    @Override protected boolean storageScroll(double x,double y,double v){if(x<leftRight&&y>=tableY&&v!=0){scroll=Math.clamp(scroll+(v<0?38:-38),0,Math.max(0,totalHeight-viewport()));initGui();return true;}return false;}
    @Override protected void storageTick(){if(rowsRevision!=TrackedStorageStore.revision()||groupsRevision!=StorageGroups.revision()){initGui();return;}if(dragging&&(mouseY<tableY+14||mouseY>tableY+viewport()-14)){int old=scroll;scroll=Math.clamp(scroll+(mouseY<tableY+14?-8:8),0,Math.max(0,totalHeight-viewport()));if(old!=scroll)initGui();}}
    @Override protected void cancelDrag(){pressedEntry=null;dragging=false;}
}
