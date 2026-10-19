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

public final class TrackedStoragesScreen extends GuiBase {
    private final Screen previous;
    private final MaterialListBase materialList;
    private String filter=StorageGroups.ALL,query="";
    private int page,tableY;
    private boolean pendingRefresh,focusSearch;
    private List<TrackedStorageStore.Entry> rows=List.of();
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
    public TrackedStoragesScreen(Screen parent,MaterialListBase list){previous=parent;materialList=list;filter=StorageGroups.selection(StorageGroups.server(),list);setParent(parent);title=tr("storages.manager");useTitleHierarchy=false;}
    static String tr(String key,Object...args){return Component.translatable(key,args).getString();}
    private int perPage(){return Math.max(1,(height-tableY-44)/38);}
    private ButtonGeneric button(int x,int y,int w,String text,Runnable action){var b=new ButtonGeneric(x,y,w,20,font.plainSubstrByWidth(text,Math.max(10,w-8)));addButton(b,(a,m)->action.run());return b;}
    public static ItemStack icon(TrackedStorageStore.Entry e){if(e.personal())return new ItemStack(Items.ENDER_CHEST);String id=e.states().isEmpty()?"minecraft:chest":e.states().getFirst().split("/",2)[0];var parsed=Identifier.tryParse(id);return parsed==null?new ItemStack(Items.CHEST):new ItemStack(BuiltInRegistries.BLOCK.getValue(parsed).asItem());}
    public static String name(TrackedStorageStore.Entry e){return e.label().isBlank()?icon(e).getHoverName().getString():e.label();}
    public static String coordinates(TrackedStorageStore.Entry e){if(e.personal())return tr("storages.manager.personal");var coords=e.positions().stream().map(p->p.getX()+", "+p.getY()+", "+p.getZ()).reduce((a,b)->a+" / "+b).orElse("");return coords+" \u00b7 "+e.key().dimension().replace("minecraft:","");}
    private void refresh(){
        String world=StorageGroups.server(),q=query.toLowerCase(Locale.ROOT);var player=mc.player;
        rows=TrackedStorageStore.snapshot().stream().filter(e->e.key().server().equals(world)&&(!e.personal()||player!=null&&e.key().identity().equals(player.getUUID().toString())))
            .filter(e->filter.equals(StorageGroups.ALL)||filter.equals(e.group()))
            .filter(e->(name(e)+" "+coordinates(e)+" "+StorageGroups.name(world,e.group())).toLowerCase(Locale.ROOT).contains(q)||contents(e).keySet().stream().anyMatch(n->n.toLowerCase(Locale.ROOT).contains(q)))
            .sorted(Comparator.comparing(TrackedStoragesScreen::name).thenComparing(e->e.key().identity())).toList();page=Math.clamp(page,0,Math.max(0,(rows.size()-1)/perPage()));
    }
    @Override public void initGui(){super.initGui();String server=StorageGroups.server();if(!StorageGroups.choices(server).contains(filter))filter=StorageGroups.ALL;int x=12,y=26;
        String selection=StorageGroups.selection(server,materialList);
        String[] texts={tr("storages.manager.filter",StorageGroups.name(server,filter)),tr("storages.manager.count",StorageGroups.name(server,selection)),tr("storages.groups.manage")};
        for(int n=0;n<texts.length;n++){
            int w=Math.min(width-24,font.width(texts[n])+12);if(x+w>width-12){x=12;y+=22;}int action=n;
            var control=button(x,y,w,texts[n],()->{if(action==0){var ids=StorageGroups.choices(server);filter=ids.get((ids.indexOf(filter)+1)%ids.size());page=0;initGui();}else if(action==1){if(StorageGroups.bound(server,materialList))StorageGroups.bind(server,materialList,filter);else StorageGroups.select(server,filter);ResourceAccounting.tick(mc);initGui();}else mc.setScreenAndShow(new StorageGroupsScreen(this));});if(action==1)control.setHoverStrings(tr("storages.manager.count.help"));x+=w+3;
        }
        x=12;y+=22;
        String binding=tr(StorageGroups.bound(server,materialList)?"storages.groups.unbind":"storages.groups.bind");
        var bind=button(x,y,Math.min(width-24,font.width(binding)+12),binding,()->{if(StorageGroups.bound(server,materialList))StorageGroups.unbind(server,materialList);else StorageGroups.bind(server,materialList,filter);initGui();});
        bind.setEnabled(StorageGroups.schematicId(materialList)!=null);bind.setHoverStrings(StorageGroups.schematicId(materialList)==null?tr("storages.groups.save_first"):tr("storages.groups.for",materialList.getName()));
        String stop=tr("storages.locate.clear");int sw=font.width(stop)+12;if(bind.getX()+bind.getWidth()+sw+6<width-12)button(bind.getX()+bind.getWidth()+3,y,sw,stop,StorageLocator::clear);
        y+=24;var search=new GuiTextFieldGeneric(12,y,width-24,16,font);search.setHint(Component.translatable("storages.manager.search"));search.setValueWrapper(query);addTextField(search,f->{query=f.getValueWrapper();page=0;pendingRefresh=true;return true;});
        search.setFocusedWrapper(focusSearch);focusSearch=false;tableY=y+22;refresh();
        int start=page*perPage();
        for(int n=0;n<perPage()&&start+n<rows.size();n++){
            var e=rows.get(start+n);int rowY=tableY+n*38;
            var locate=button(width-157,rowY+2,48,tr("storages.locate"),()->{if(StorageLocator.show(e))mc.setScreenAndShow(null);});locate.setEnabled(StorageLocator.canShow(e));if(!StorageLocator.canShow(e))locate.setHoverStrings(tr("storages.locate.unavailable"));
            button(width-107,rowY+2,43,tr("storages.edit"),()->mc.setScreenAndShow(new StorageEntryScreen(this,e.key())));
            button(width-62,rowY+2,50,tr("storages.remove"),()->mc.setScreenAndShow(new ConfirmScreen(yes->{if(yes)TrackedStorageStore.remove(e.key());mc.setScreenAndShow(this);},Component.translatable("storages.remove"),Component.literal(name(e)+" \u00b7 "+coordinates(e)))));
        }
        button(12,height-28,30,"<",()->{page=Math.max(0,page-1);initGui();});button(44,height-28,30,">",()->{if((page+1)*perPage()<rows.size())page++;initGui();});
        button(width-90,height-28,78,tr("litematicainventories.done"),this::onClose);
    }
    @Override protected void drawScreenBackground(GuiContext g,int mx,int my){
        super.drawScreenBackground(g,mx,my);int start=page*perPage();
        for(int n=0;n<perPage()&&start+n<rows.size();n++){int y=tableY+n*38;g.fill(10,y-1,width-10,y+36,n%2==0?0xA0303030:0xA0101010);}
    }
    @Override protected void drawContents(GuiContext g,int mx,int my,float dt){
        int start=page*perPage(),textWidth=Math.max(55,width-196);
        for(int n=0;n<perPage()&&start+n<rows.size();n++){
            var e=rows.get(start+n);int y=tableY+n*38;
            g.item(icon(e),13,y+3);
            g.text(font,font.plainSubstrByWidth(name(e),textWidth),33,y+3,e.valid()?0xFFFFFFFF:0xFF999999);
            g.text(font,font.plainSubstrByWidth(detail(e),textWidth),33,y+14,0xFFAAAAAA);
            g.text(font,font.plainSubstrByWidth(coordinates(e),width-30),13,y+26,0xFFCCCCCC);
            if(mx<width-160&&my>=y&&my<y+36)g.getGuiGraphics().setTooltipForNextFrame(font,List.of(Component.literal(name(e)),Component.literal(coordinates(e)),Component.literal(StorageGroups.name(e.key().server(),e.group())),Component.translatable(e.valid()?"storages.manager.saved":"storages.manager.invalid")),Optional.empty(),mx,my);
        }
        if(rows.isEmpty())g.text(font,tr("storages.manager.empty"),12,tableY+5,0xFFAAAAAA);
        g.text(font,(page+1)+" / "+Math.max(1,(rows.size()+perPage()-1)/perPage())+" \u00b7 "+rows.size(),82,height-22,0xFFFFFFFF);
    }
    @Override public boolean onMouseScrolled(double x,double y,double h,double v){if(y>=tableY&&v!=0){page=Math.clamp(page+(v<0?1:-1),0,Math.max(0,(rows.size()-1)/perPage()));initGui();return true;}return super.onMouseScrolled(x,y,h,v);}
    @Override public void tick(){super.tick();if(pendingRefresh){pendingRefresh=false;focusSearch=true;initGui();}}
    @Override public void onClose(){mc.setScreenAndShow(previous);}
    @Override public boolean isPauseScreen(){return false;}
}
