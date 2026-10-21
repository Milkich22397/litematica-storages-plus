package dev.asaul.litematicainventories;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import fi.dy.masa.litematica.materials.*;
import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.*;
import dev.asaul.litematicainventories.mixin.GuiBaseButtonsAccess;
import java.util.*;

/** Clicks the actual transformed widgets in the generated test world. */
public final class StorageUiChecks {
    private static int stage,ticks;
    private static MaterialListSchematic list;
    private static TrackedStoragesScreen manager;
    private static String group;
    private static TrackedStorageStore.Key locatedKey;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object field(Object o,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static MouseButtonEvent click(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));}
    private static List<ButtonBase> buttons(Minecraft c){return ((GuiBaseButtonsAccess)c.gui.screen()).storage$buttons();}
    private static String text(ButtonBase b)throws Exception{return (String)field(b,ButtonBase.class,"displayString");}
    private static void press(Minecraft c,String key)throws Exception{if(c.gui.screen() instanceof StorageDashboard d){String id=key.equals("storages.groups.bind")?"binding":key;if(d.controls.containsKey(id)){MenuUpdateChecks.press(c,id);return;}}String value=Component.translatable(key).getString();for(var b:buttons(c))if(text(b).equals(value)){((GuiBase)c.gui.screen()).onMouseClicked(click(b.getX()+3,b.getY()+3),false);return;}throw new AssertionError("Missing button "+value);}
    private static void at(Minecraft c,int y){var b=buttons(c).stream().filter(x->x.getY()==y).findFirst().orElseThrow();((GuiBase)c.gui.screen()).onMouseClicked(click(b.getX()+3,b.getY()+3),false);}
    private static void input(Minecraft c,String value)throws Exception{
        var gui=(GuiBase)c.gui.screen();var wrappers=(List<fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper<?>>)field(gui,GuiBase.class,"textFields");var w=wrappers.getFirst();var f=w.textField();
        f.setValueWrapper("");((fi.dy.masa.malilib.gui.interfaces.ITextFieldListener)w.listener()).onTextChange(f);gui.onMouseClicked(click(f.getX()+3,f.getY()+3),false);
        for(int cp:value.codePoints().toArray())gui.onCharTyped(new CharacterEvent(cp));
    }
    private static void shot(Minecraft c,String name){net.minecraft.client.Screenshot.grab(new java.io.File("."),name,c.gameRenderer.mainRenderTarget(),1,msg->{});}
    private static void next(){stage++;ticks=0;}
    static void start(Minecraft c,fi.dy.masa.litematica.schematic.LitematicaSchematic schematic){
        stage=0;ticks=0;list=new MaterialListSchematic(schematic,false);list.setMaterialListEntries(List.of(new MaterialListEntry(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE),640,640,0,128)));manager=new TrackedStoragesScreen(new ResourceSettingsScreen(new SettingsScreen(null)),list);c.setScreenAndShow(manager);
    }
    static boolean tick(Minecraft c,List<String> log)throws Exception{
        if(++ticks>150)throw new AssertionError("Storage UI timeout "+stage);
        switch(stage){
            case 0 -> {if(ticks==15){press(c,"storages.groups.manage");input(c,"Live build");press(c,"storages.groups.create");group=StorageGroups.groups(StorageGroups.server()).entrySet().stream().filter(e->e.getValue().equals("Live build")).findFirst().orElseThrow().getKey();next();}}
            case 1 -> {if(ticks==15){shot(c,"storage-groups-editor.png");press(c,"litematicainventories.done");press(c,"storages.edit");input(c,"Main supply chest");at(c,98);check(field(c.gui.screen(),StorageEntryScreen.class,"group").equals(group),"entry group selected by real button");press(c,"storages.save");next();}}
            case 2 -> {if(ticks==15){MenuUpdateChecks.press(c,"filter");check(field(manager,TrackedStoragesScreen.class,"filter").equals(""),"first filter step is ungrouped");MenuUpdateChecks.press(c,"filter");check(field(manager,TrackedStoragesScreen.class,"filter").equals(group),"filter chooses created group");
                for(int step=0;step<2;step++)MenuUpdateChecks.press(c,"count");
                check(StorageGroups.selection(StorageGroups.server(),null).equals(group),"count button cycles independently of filter");press(c,"storages.groups.bind");StorageGroups.select(StorageGroups.server(),StorageGroups.ALL);check(StorageGroups.bound(StorageGroups.server(),list)&&StorageGroups.selection(StorageGroups.server(),list).equals(group),"binding retains selection independently from global mode");input(c,"Stone");next();}}
            case 3 -> {if(ticks==15){check(((List<?>)field(manager,TrackedStoragesScreen.class,"rows")).size()==1,"native item query finds chest by saved contents");
                var cachedRows=field(manager,TrackedStoragesScreen.class,"rows");var cachedLines=field(manager,TrackedStoragesScreen.class,"lines");int table=(int)field(manager,TrackedStoragesScreen.class,"tableY");
                manager.mouseScrolled(20,table+30,0,-1);check(field(manager,TrackedStoragesScreen.class,"rows")==cachedRows&&field(manager,TrackedStoragesScreen.class,"lines")==cachedLines,"scroll reuses filtered sorted rows and grouped layout");
                var entry=((List<TrackedStorageStore.Entry>)cachedRows).getFirst();TrackedStorageStore.edit(entry.key(),"Audited stock",entry.group());manager.tick();
                check(field(manager,TrackedStoragesScreen.class,"rows")!=cachedRows&&((List<TrackedStorageStore.Entry>)field(manager,TrackedStoragesScreen.class,"rows")).getFirst().label().equals("Audited stock"),"ledger edits refresh cached manager rows while open");
                TrackedStorageStore.edit(entry.key(),entry.label(),entry.group());manager.tick();shot(c,"tracked-storages-search.png");input(c,"Main supply");manager.tick();check(((List<?>)field(manager,TrackedStoragesScreen.class,"rows")).size()==1,"storage search matches custom name");input(c,"Live build");manager.tick();check(((List<?>)field(manager,TrackedStoragesScreen.class,"rows")).size()==1,"storage search matches group name");input(c,"Diamond");next();}}
            case 4 -> {if(ticks==15){check(((List<?>)field(manager,TrackedStoragesScreen.class,"rows")).isEmpty(),"item query excludes containers without item");input(c,"");next();}}
            case 5 -> {if(ticks==15){
                MenuUpdateChecks.press(c,"filter");check(field(manager,TrackedStoragesScreen.class,"filter").equals(StorageGroups.ALL),"display all remains independent of bound counted group");
                int table=(int)field(manager,TrackedStoragesScreen.class,"tableY");var entries=TrackedStorageStore.snapshot().stream().filter(e->e.group().equals(group)).toList();var key=entries.getFirst().key();
                manager.mouseClicked(click(40,table+48),false);manager.mouseDragged(click(40,table+7),0,-41);manager.mouseReleased(click(40,table+7));
                check(TrackedStorageStore.get(key).group().isEmpty()&&ResourceAccounting.available(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE),list)==128,"real drag to ungrouped heading updates membership and bound count");
                manager.mouseClicked(click(40,table+28),false);manager.mouseDragged(click(40,table+65),0,37);manager.mouseReleased(click(40,table+65));
                check(TrackedStorageStore.get(key).group().equals(group)&&ResourceAccounting.available(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE),list)==448,"drag back to named group restores bound total");
                manager.mouseClicked(click(40,table+28),false);check(((Set<?>)field(manager,TrackedStoragesScreen.class,"collapsed")).contains(group),"group heading collapses on click");
                shot(c,"tracked-storages-collapsed.png");manager.mouseClicked(click(40,table+28),false);
                MenuUpdateChecks.press(c,"count");
                check(StorageGroups.selection(StorageGroups.server(),list).equals(StorageGroups.ALL)&&field(manager,TrackedStoragesScreen.class,"filter").equals(StorageGroups.ALL),"can change counted group while displaying all");
                StorageGroups.bind(StorageGroups.server(),list,group);manager.initGui();shot(c,"tracked-storages.png");c.getWindow().setWindowed(640,480);c.options.guiScale().set(2);c.resizeGui();next();}}
            case 6 -> {if(ticks==20){int table=(int)field(manager,TrackedStoragesScreen.class,"tableY");check(table<c.getWindow().getGuiScaledHeight()-60,"manager preserves visible rows at 320x240");for(var b:buttons(c))check(b.getX()>=0&&b.getY()>=0&&b.getX()+b.getWidth()<=c.getWindow().getGuiScaledWidth()&&b.getY()+b.getHeight()<=c.getWindow().getGuiScaledHeight(),"manager button stays in narrow window");shot(c,"tracked-storages-narrow.png");c.getWindow().setWindowed(1920,1080);c.resizeGui();next();}}
            case 7 -> {if(ticks==20){press(c,"storages.locate");check(c.gui.screen()==null&&StorageLocator.target()!=null,"locate button closes menu and targets chest");locatedKey=StorageLocator.target();var target=TrackedStorageStore.get(locatedKey);var p=target.positions().getFirst();double dx=p.getX()+.5-c.player.getX(),dz=p.getZ()+.5-c.player.getZ(),dy=p.getY()+.5-c.player.getEyeY();c.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));c.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));next();}}
            case 8 -> {if(ticks==20){shot(c,"tracked-storage-highlight.png");check(StorageLocator.target()!=null,"world renderer retains valid highlight");var p=TrackedStorageStore.get(locatedKey).positions().getFirst();c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p),net.minecraft.core.Direction.UP,p,false));next();}}
            case 9 -> {var opening=ContainerRecorder.active();if(opening!=null&&opening.trackingKey.equals(locatedKey)&&opening.synchronizedMenu&&ticks>12){check(StorageLocator.target()==null&&StorageLocator.opacity(System.nanoTime())==0,"opening the actual located server chest dismisses and finishes fading the outline");shot(c,"tracked-storage-opened.png");
                var cacheKey=new CacheKey(opening.server,opening.dimension,opening.adapter.halves().getFirst());var prior=SessionInventoryCache.get(cacheKey);
                var recorded=ContainerRecorder.Opening.class.getDeclaredField("lastRecorded");recorded.setAccessible(true);recorded.setLong(opening,0);ContainerRecorder.received(opening.menu.containerId,false);
                var fresh=SessionInventoryCache.get(cacheKey);check(prior!=null&&fresh!=prior&&fresh.observedAt()>=prior.observedAt(),"unchanged synchronized menu refreshes schematic observation age periodically");
                ContainerRecorder.received(opening.menu.containerId,false);check(SessionInventoryCache.get(cacheKey)==fresh,"unchanged menu does not rewrite schematic cache every tick");
                var screen=c.gui.screen();var edit=screen.children().stream().filter(w->w instanceof ContainerNameField).map(w->(ContainerNameField)w).findFirst().orElseThrow();
                check(edit.visible&&!edit.isFocused()&&!edit.isBordered(),"tracked title is an unfocused transparent input without a separate rename button");
                check(screen.children().stream().noneMatch(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("...")),"old rename button removed");
                var track=screen.children().stream().filter(w->w instanceof TrackingButton).map(w->(TrackingButton)w).findFirst().orElseThrow();check(edit.getX()+edit.getWidth()<=track.getX()||track.getX()+track.getWidth()<=edit.getX()||edit.getY()+edit.getHeight()<=track.getY()||track.getY()+track.getHeight()<=edit.getY(),"name input avoids the moved tracking icon");
                screen.mouseClicked(click(edit.getX()+3,edit.getY()+3),false);check(edit.isFocused(),"clicking title focuses input");edit.setValue("");for(int cp:"Local supply".codePoints().toArray())screen.charTyped(new CharacterEvent(cp));screen.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,0,0));
                check(edit.visible&&!edit.isFocused()&&c.gui.screen()==screen&&c.player.containerMenu==opening.menu&&TrackedStorageStore.get(locatedKey).label().equals("Local supply"),"Enter saves and leaves the title field visible without closing menu");
                screen.mouseClicked(click(edit.getX()+3,edit.getY()+3),false);edit.setValue("Discarded");screen.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0));check(edit.visible&&c.gui.screen()==screen&&edit.getValue().equals("Local supply")&&TrackedStorageStore.get(locatedKey).label().equals("Local supply"),"Escape restores saved name and leaves chest open");
                screen.mouseClicked(click(edit.getX()+3,edit.getY()+3),false);edit.setValue("Click-away name");screen.mouseClicked(click(edit.getX(),edit.getY()-4),false);check(TrackedStorageStore.get(locatedKey).label().equals("Click-away name")&&!edit.isFocused(),"click-away commits title without eating the normal click");
                screen.setFocused(edit);edit.setValue("Keyboard focused name");screen.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,0,0));check(TrackedStorageStore.get(locatedKey).label().equals("Keyboard focused name"),"keyboard focus saves title edit");
                edit.setFocused(true);edit.setValue("Focus lost name");edit.setFocused(false);edit.synchronize(screen.getTitle());check(TrackedStorageStore.get(locatedKey).label().equals("Focus lost name"),"focus loss saves title edit before refreshing label");
                TrackedStorageStore.edit(locatedKey,"Local supply",TrackedStorageStore.get(locatedKey).group());
                double scale=c.getWindow().getGuiScale();c.mouseHandler.onMove(c.getWindow().handle(),(track.getX()+6)*scale,(track.getY()+6)*scale,0,0);next();}}
            case 10 -> {if(ticks==15){shot(c,"tracked-storage-renamed.png");shot(c,"tracking-tooltip-lines.png");var edit=c.gui.screen().children().stream().filter(w->w instanceof ContainerNameField).map(w->(ContainerNameField)w).findFirst().orElseThrow();c.gui.screen().mouseClicked(click(edit.getX()+3,edit.getY()+3),false);check(edit.isFocused(),"title edit focused before closing");edit.setValue("Saved on close");net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_F9));next();}}
            case 11 -> {if(ticks>5&&c.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen){check(c.player.containerMenu==c.player.inventoryMenu,"F9 confirmation closes the server menu safely");check(TrackedStorageStore.get(locatedKey).label().equals("Saved on close"),"closing container saves focused title edit");var cancel=c.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).toList().get(1);c.gui.screen().mouseClicked(click(cancel.getX()+3,cancel.getY()+3),false);check(c.gui.screen()==null,"F9 cancel does not reopen a stale container screen");check(ResourceAccounting.available(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE),list)==448,"bound schematic resource count remains correct through menus");log.add("STORAGE UI LIVE PASS: typed group creation, label editing, membership, filters, per-file binding, item-content search, 320x240 controls, real Locate click, world highlight and dismissal after opening the actual target; inline local rename with Enter/Escape; collapsible groups, drag/drop and independent count selection");c.setScreenAndShow(new ResourceSettingsScreen(new SettingsScreen(null)));return true;}}
        }return false;
    }
    private StorageUiChecks(){}
}
