package dev.asaul.litematicainventories;

import com.google.gson.Gson;
import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shared tabs and sidebar. Preferences can be reverted; confirmed ledger operations are immediate. */
public abstract class StorageDashboard extends GuiBase {
    protected final Screen previous;
    protected final MaterialListBase materialList;
    protected final List<CacheKey> containerKeys;
    private final StorageSettings before;
    protected int tab,leftRight,railX,railWidth;
    protected String query="";
    private final String[] searches={"","",""};
    private final int[] offsets={0,0,0};
    private int optionOffset,optionCount,infoOffset;
    protected boolean pendingRefresh,focusSearch;
    private String selected="",infoTitle="",infoBody="";
    private boolean invalidInput;
    private final Set<String> invalidFields=new HashSet<>();
    private final Map<String,String> drafts=new HashMap<>();
    private record Region(String key,int top,int bottom){}
    private final List<Region> regions=new ArrayList<>();
    private enum Kind { BOOL,INT,LONG,TEXT,COLOR }
    private record Option(String field,String label,String help,Kind kind,long min,long max){}
    private final Map<String,GuiTextFieldGeneric> inputs=new HashMap<>();
    protected final Map<String,ButtonGeneric> controls=new HashMap<>();
    private static final List<Option> PREFS=List.of(
        new Option("resourceTracking","dashboard.accounting","dashboard.accounting.help",Kind.BOOL,0,0),
        new Option("includeTrackedResources","dashboard.sources","dashboard.sources.help",Kind.BOOL,0,0),
        new Option("resourceBreakdown","dashboard.breakdown","storages.breakdown.help",Kind.BOOL,0,0),
        new Option("showTrackingButton","dashboard.icon","dashboard.icon.help",Kind.BOOL,0,0),
        new Option("namespace","storage.profile","dashboard.profile.help",Kind.TEXT,1,100),
        new Option("maxEntries","storage.limit","dashboard.limit.help",Kind.INT,1,200000),
        new Option("missingColor","dashboard.color.missing","dashboard.color.help",Kind.COLOR,0,0),
        new Option("extraColor","dashboard.color.extra","dashboard.color.help",Kind.COLOR,0,0),
        new Option("countColor","storage.color.count","dashboard.color.help",Kind.COLOR,0,0),
        new Option("wrongColor","dashboard.color.wrong","dashboard.color.help",Kind.COLOR,0,0),
        new Option("unknownColor","dashboard.color.unknown","dashboard.color.help",Kind.COLOR,0,0));
    private static final List<Option> DATA=List.of(
        new Option("ttlHours","storage.ttl","dashboard.ttl.help",Kind.LONG,0,876000),
        new Option("cleanupHours","storage.cleanup.age","dashboard.cleanup.help",Kind.LONG,1,876000));
    protected StorageDashboard(Screen parent,MaterialListBase list,int initialTab,List<CacheKey> keys){
        previous=parent instanceof StorageDashboard d?d.previous:parent;materialList=list;tab=initialTab;
        before=copy(parent instanceof StorageDashboard d?d.before:StorageSettings.INSTANCE);
        var opening=ContainerRecorder.active();containerKeys=keys!=null?List.copyOf(keys):parent instanceof StorageDashboard d?d.containerKeys:opening==null?List.of():opening.adapter.halves().stream().map(p->new CacheKey(opening.server,opening.dimension,p)).toList();
        setParent(previous);title="Litematica Storages+";useTitleHierarchy=false;
    }
    private static StorageSettings copy(StorageSettings s){var g=new Gson();return g.fromJson(g.toJson(s),StorageSettings.class);}
    protected static String tr(String key,Object...args){return Component.translatable(key,args).getString();}
    protected ButtonGeneric button(String id,int x,int y,int w,String text,Runnable action){
        var b=new ButtonGeneric(x,y,Math.max(16,w),20,font.plainSubstrByWidth(text,Math.max(8,w-8)));if(font.width(text)>w-8)b.setHoverStrings(text);addButton(b,(a,m)->action.run());controls.put(id,b);return b;
    }
    protected void describe(String key,String heading,String body){if(!selected.equals(key)||!infoBody.equals(body)){selected=key;infoTitle=heading;infoBody=body;infoOffset=0;}}
    protected abstract void initStorages();
    protected abstract void drawStorages(GuiContext g,int mx,int my,float dt);
    protected abstract void drawStorageBackground(GuiContext g,int mx,int my);
    protected abstract boolean storageClick(MouseButtonEvent event,boolean doubleClick);
    protected abstract boolean storageDrag(MouseButtonEvent event,double dx,double dy);
    protected abstract boolean storageRelease(MouseButtonEvent event);
    protected abstract boolean storageScroll(double x,double y,double v);
    protected abstract void storageTick();
    protected abstract void cancelDrag();
    public final void selectTab(int next){if(next==tab)return;cancelDrag();searches[tab]=query;offsets[tab]=optionOffset;tab=next;query=searches[tab];optionOffset=offsets[tab];selected="";infoTitle="";infoBody="";infoOffset=0;focusSearch=false;initGui();}
    @Override public final void initGui(){
        super.initGui();controls.clear();inputs.clear();regions.clear();
        railWidth=Math.max(110,width*31/100);railX=width-railWidth-8;leftRight=railX-8;
        int tw=(width-20)/3;String[] names={"dashboard.tab.storages","dashboard.tab.preferences","dashboard.tab.data"};
        for(int n=0;n<3;n++){int target=n;var b=button("tab."+n,8+n*(tw+2),6,tw,tr(names[n]),()->selectTab(target));b.setEnabled(n!=tab);}
        // Search is always the first text field, so rebuilding filtered rows retains its focus.
        var search=new GuiTextFieldGeneric(railX+4,height-124,railWidth-8,18,font);search.setMaxLengthWrapper(120);search.setHint(Component.translatable(tab==0?"dashboard.search.storage":"dashboard.search.setting"));search.setValueWrapper(query);
        addTextField(search,f->{query=f.getValueWrapper();optionOffset=0;pendingRefresh=true;return true;});search.setFocusedWrapper(focusSearch);focusSearch=false;
        if(tab==0)initStorages();else initOptions();
        var s=StorageSettings.INSTANCE;
        button("recording",railX+4,height-100,railWidth-8,tr(s.recording?"litematicainventories.recording.on":"litematicainventories.recording.off"),()->{s.recording=!s.recording;describe("recording",tr(s.recording?"litematicainventories.recording.on":"litematicainventories.recording.off"),tr("dashboard.recording.help"));ContainerRecorder.recordingChanged();initGui();});
        button("overlay",railX+4,height-76,railWidth-8,tr(s.overlay?"storage.overlay.on":"storage.overlay.off"),()->{s.overlay=!s.overlay;describe("overlay",tr(s.overlay?"storage.overlay.on":"storage.overlay.off"),tr("dashboard.overlay.help"));initGui();});
        int half=(railWidth-12)/2;
        button("reset",railX+4,height-52,half,tr("dashboard.reset"),this::reset).setEnabled(tab!=0);
        button("cancel",railX+8+half,height-52,half,tr("gui.cancel"),this::cancel).setHoverStrings(tr("dashboard.cancel.help"));
        button("done",railX+4,height-28,railWidth-8,tr("litematicainventories.done"),this::commit).setEnabled(!invalidInput);
    }
    private List<Option> options(){return tab==1?PREFS:DATA;}
    private Object value(Option o){try{return StorageSettings.class.getField(o.field()).get(StorageSettings.INSTANCE);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private void put(Option o,Object value){try{StorageSettings.class.getField(o.field()).set(StorageSettings.INSTANCE,value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}ResourceAccounting.tick(mc);}
    private boolean matches(String label,String help){String q=query.strip().toLowerCase(Locale.ROOT);return q.isEmpty()||(tr(label)+" "+tr(help)).toLowerCase(Locale.ROOT).contains(q);}
    private void option(Option o,int y){
        int w=leftRight-16;regions.add(new Region(o.field(),y,y+24));String label=tr(o.label());
        if(o.kind()==Kind.BOOL){String status=tr((Boolean)value(o)?"dashboard.yes":"dashboard.no");button(o.field(),8,y,w,font.plainSubstrByWidth(label,Math.max(20,w-font.width(status)-20))+"  "+status,()->{describe(o.field(),label,tr(o.help()));put(o,!(Boolean)value(o));initGui();});}
        else{
            int iw=Math.min(100,Math.max(62,w/3));button(o.field()+".label",8,y,w-iw-4,label,()->describe(o.field(),label,tr(o.help())));
            var f=new GuiTextFieldGeneric(leftRight-8-iw,y+2,iw,16,font);f.setMaxLengthWrapper(o.kind()==Kind.TEXT?100:18);f.setValueWrapper(drafts.getOrDefault(o.field(),o.kind()==Kind.COLOR?String.format("%08X",(int)value(o)):value(o).toString()));inputs.put(o.field(),f);
            addTextField(f,t->{describe(o.field(),label,tr(o.help()));drafts.put(o.field(),t.getValueWrapper());boolean valid=update(o,t.getValueWrapper());if(valid)invalidFields.remove(o.field());else invalidFields.add(o.field());invalidInput=!invalidFields.isEmpty();var done=controls.get("done");if(done!=null)done.setEnabled(!invalidInput);var clean=controls.get("storage.cleanup");if(clean!=null)clean.setEnabled(!invalidFields.contains("cleanupHours"));return valid;});
        }
    }
    private boolean update(Option o,String text){try{
        switch(o.kind()){
            case TEXT -> {if(text.isBlank()||text.length()>o.max())return false;put(o,text);}
            case COLOR -> {if(!text.matches("[0-9a-fA-F]{8}"))return false;put(o,(int)Long.parseLong(text,16));}
            case INT -> {long v=Long.parseLong(text);if(v<o.min()||v>o.max())return false;put(o,(int)v);}
            case LONG -> {long v=Long.parseLong(text);if(v<o.min()||v>o.max())return false;put(o,v);}
            default -> {return false;}
        }return true;
    }catch(NumberFormatException e){return false;}}
    private record Action(String id,String label,String help,Runnable run){}
    private List<Action> actions(){
        if(tab==1)return List.of(new Action("storages.reset_position","storages.reset_position","dashboard.position.help",()->{StorageSettings.INSTANCE.trackingPositions.clear();StorageSettings.arrangeTrackingButton=false;}),new Action("storage.placement","storage.placement","storage.placement.help",()->{var m=DataManager.getSchematicPlacementManager();var placements=m.getAllSchematicsPlacements().stream().filter(p->p.isEnabled()).sorted(Comparator.comparing(p->p.getHashId().toString())).toList();if(!placements.isEmpty()){int i=placements.indexOf(m.getSelectedSchematicPlacement());m.setSelectedSchematicPlacement(placements.get((i+1)%placements.size()));}}));
        return List.of(
            new Action("storage.cleanup","storage.cleanup","storage.cleanup.confirm",()->{long age=StorageSettings.INSTANCE.cleanupHours;confirm(Component.translatable("storage.cleanup.confirm",age),()->{SessionInventoryCache.removeOlderThan(age);TrackedStorageStore.removeOlderThan(age);ResourceAccounting.tick(mc);});}),
            new Action("storage.clear.container","storage.clear.container","storage.clear.container.confirm",()->confirm(Component.translatable("storage.clear.container.confirm"),()->{containerKeys.forEach(SessionInventoryCache::remove);SessionInventoryCache.flush();})),
            new Action("storage.clear.world","storage.clear.world","storage.clear.world.confirm",()->confirm(Component.translatable("storage.clear.world.confirm"),SessionInventoryCache::clearWorld)),
            new Action("storage.clear.all","storage.clear.all","storage.clear.all.confirm",()->confirm(Component.translatable("storage.clear.all.confirm"),SessionInventoryCache::clear)),
            new Action("storages.clear","storages.clear","storages.clear.confirm",()->confirm(Component.translatable("storages.clear.confirm"),()->{TrackedStorageStore.clear();ResourceAccounting.tick(mc);})));
    }
    private void confirm(Component body,Runnable action){mc.setScreenAndShow(new ConfirmScreen(yes->{if(yes)action.run();mc.setScreenAndShow(this);},Component.translatable("storage.clear.confirm.title"),body));}
    private void initOptions(){
        var opts=options().stream().filter(o->matches(o.label(),o.help())).toList();var acts=actions().stream().filter(a->matches(a.label(),a.help())).toList();optionCount=opts.size()+acts.size();int capacity=Math.max(1,(height-66)/26);optionOffset=Math.clamp(optionOffset,0,Math.max(0,optionCount-capacity));
        int row=0;for(var o:opts){if(row>=optionOffset&&row<optionOffset+capacity)option(o,34+(row-optionOffset)*26);row++;}
        for(var a:acts){if(row>=optionOffset&&row<optionOffset+capacity){int y=34+(row-optionOffset)*26;regions.add(new Region(a.id(),y,y+24));var b=button(a.id(),8,y,leftRight-16,tr(a.label()),()->{describe(a.id(),tr(a.label()),tr(a.help(),StorageSettings.INSTANCE.cleanupHours));a.run().run();if(mc.gui.screen()==this)initGui();});if(a.id().equals("storage.cleanup"))b.setEnabled(!invalidFields.contains("cleanupHours"));}row++;}
        button("scroll.up",8,height-28,26,"<",()->{optionOffset=Math.max(0,optionOffset-capacity);initGui();});button("scroll.down",38,height-28,26,">",()->{optionOffset=Math.min(Math.max(0,optionCount-capacity),optionOffset+capacity);initGui();});
    }
    private void reset(){
        if(selected.equals("recording")||selected.equals("overlay")){var defaults=new StorageSettings();if(selected.equals("recording")){StorageSettings.INSTANCE.recording=defaults.recording;ContainerRecorder.recordingChanged();}else StorageSettings.INSTANCE.overlay=defaults.overlay;initGui();return;}
        var defaults=new StorageSettings();var opts=options();var chosen=opts.stream().filter(o->o.field().equals(selected)).findFirst();
        for(var o:chosen.isPresent()?List.of(chosen.get()):opts)try{put(o,StorageSettings.class.getField(o.field()).get(defaults));drafts.remove(o.field());invalidFields.remove(o.field());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        invalidInput=!invalidFields.isEmpty();initGui();
    }
    private void commit(){if(invalidInput)return;StorageSettings.save();cancelDrag();mc.setScreenAndShow(previous);}
    private void cancel(){boolean recording=StorageSettings.INSTANCE.recording;StorageSettings.INSTANCE=copy(before);if(recording!=StorageSettings.INSTANCE.recording)ContainerRecorder.recordingChanged();ResourceAccounting.tick(mc);StorageSettings.save();cancelDrag();mc.setScreenAndShow(previous);}
    @Override public final void onClose(){cancel();}
    @Override public boolean onKeyTyped(net.minecraft.client.input.KeyEvent event){if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE){cancel();return true;}return super.onKeyTyped(event);}
    @Override protected final void drawTitle(GuiContext g,int mx,int my,float dt){}
    @Override protected final void drawScreenBackground(GuiContext g,int mx,int my){super.drawScreenBackground(g,mx,my);g.fill(4,30,leftRight,height-4,0x90202020);g.fill(railX,30,width-4,height-4,0xB0181818);g.fill(railX-4,30,railX-2,height-4,0xFF777777);if(tab==0)drawStorageBackground(g,mx,my);else for(var r:regions)if(r.key().equals(selected))g.fill(6,r.top()-1,leftRight-6,r.bottom(),0x705599FF);}
    @Override protected final void drawContents(GuiContext g,int mx,int my,float dt){
        if(tab==0)drawStorages(g,mx,my,dt);else if(optionCount==0)g.text(font,tr("dashboard.empty"),10,40,0xFFBBBBBB);
        String heading=infoTitle.isBlank()?tr("dashboard.info.title"):infoTitle;String body=infoBody.isBlank()?tr(tab==0?"dashboard.info.storage":"dashboard.info.setting"):infoBody;
        if(invalidInput)body=tr("dashboard.invalid")+"\n\n"+body;
        int w=railWidth-16;var titleLines=font.split(Component.literal(heading),w);int y=tab==0?62:38;int maxY=height-132;
        for(int n=0;n<Math.min(2,titleLines.size())&&y+10<=maxY;n++,y+=11)g.text(font,titleLines.get(n),railX+8,y,0xFFFFFFFF);
        y+=5;int infoStart=y;var lines=font.split(Component.literal(body),w);int capacity=Math.max(0,(maxY-y)/11);infoOffset=Math.clamp(infoOffset,0,Math.max(0,lines.size()-Math.max(1,capacity)));
        for(int n=infoOffset;n<lines.size()&&y+10<=maxY;n++,y+=11)g.text(font,lines.get(n),railX+8,y,0xFFBBBBBB);
        if(capacity>0&&lines.size()>capacity){int track=maxY-infoStart,handle=Math.max(6,track*capacity/lines.size());int top=infoStart+(track-handle)*infoOffset/(lines.size()-capacity);g.fill(width-10,infoStart,width-8,maxY,0xFF444444);g.fill(width-10,top,width-8,top+handle,0xFFBBBBBB);}
        if(tab!=0)g.text(font,tr("dashboard.list.count",optionCount),72,height-22,0xFFAAAAAA);
    }
    @Override public final boolean onMouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(tab==0&&storageClick(event,doubleClick))return true;
        if(tab!=0&&event.x()<leftRight)for(var r:regions)if(event.y()>=r.top()&&event.y()<r.bottom()){
            for(var o:options())if(o.field().equals(r.key()))describe(o.field(),tr(o.label()),tr(o.help()));
            for(var a:actions())if(a.id().equals(r.key()))describe(a.id(),tr(a.label()),tr(a.help(),StorageSettings.INSTANCE.cleanupHours));
        }return super.onMouseClicked(event,doubleClick);
    }
    @Override public final boolean onMouseDragged(MouseButtonEvent event,double dx,double dy){return tab==0&&storageDrag(event,dx,dy)||super.onMouseDragged(event,dx,dy);}
    @Override public final boolean onMouseReleased(MouseButtonEvent event){return tab==0&&storageRelease(event)||super.onMouseReleased(event);}
    @Override public final boolean onMouseScrolled(double x,double y,double h,double v){if(v!=0&&x>=railX&&y>=34&&y<height-128){infoOffset=Math.max(0,infoOffset+(v<0?2:-2));return true;}if(tab==0&&storageScroll(x,y,v))return true;if(tab!=0&&x<leftRight&&y>=32&&v!=0){optionOffset=Math.max(0,optionOffset+(v<0?1:-1));initGui();return true;}return super.onMouseScrolled(x,y,h,v);}
    @Override public final void tick(){super.tick();if(pendingRefresh){pendingRefresh=false;focusSearch=true;initGui();}if(tab==0)storageTick();}
    @Override public boolean isPauseScreen(){return false;}
}
