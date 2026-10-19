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
    private static void press(Minecraft c,String key)throws Exception{String value=Component.translatable(key).getString();for(var b:buttons(c))if(text(b).equals(value)){((GuiBase)c.gui.screen()).onMouseClicked(click(b.getX()+3,b.getY()+3),false);return;}throw new AssertionError("Missing button "+value);}
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
            case 2 -> {if(ticks==15){at(c,26);check(field(manager,TrackedStoragesScreen.class,"filter").equals(""),"first filter step is ungrouped");at(c,26);check(field(manager,TrackedStoragesScreen.class,"filter").equals(group),"filter chooses created group");
                for(var b:buttons(c))if(text(b).startsWith("Count:")){((GuiBase)c.gui.screen()).onMouseClicked(click(b.getX()+3,b.getY()+3),false);break;}
                check(StorageGroups.selection(StorageGroups.server(),null).equals(group),"count button applies filtered group");press(c,"storages.groups.bind");StorageGroups.select(StorageGroups.server(),StorageGroups.ALL);check(StorageGroups.bound(StorageGroups.server(),list)&&StorageGroups.selection(StorageGroups.server(),list).equals(group),"binding retains selection independently from global mode");input(c,"Stone");next();}}
            case 3 -> {if(ticks==15){check(((List<?>)field(manager,TrackedStoragesScreen.class,"rows")).size()==1,"native item query finds chest by saved contents");shot(c,"tracked-storages-search.png");input(c,"Diamond");next();}}
            case 4 -> {if(ticks==15){check(((List<?>)field(manager,TrackedStoragesScreen.class,"rows")).isEmpty(),"item query excludes containers without item");input(c,"");next();}}
            case 5 -> {if(ticks==15){shot(c,"tracked-storages.png");c.getWindow().setWindowed(640,480);c.options.guiScale().set(2);c.resizeGui();next();}}
            case 6 -> {if(ticks==20){int table=(int)field(manager,TrackedStoragesScreen.class,"tableY");check(table<c.getWindow().getGuiScaledHeight()-60,"manager preserves visible rows at 320x240");for(var b:buttons(c))check(b.getX()>=0&&b.getY()>=0&&b.getX()+b.getWidth()<=c.getWindow().getGuiScaledWidth()&&b.getY()+b.getHeight()<=c.getWindow().getGuiScaledHeight(),"manager button stays in narrow window");shot(c,"tracked-storages-narrow.png");c.getWindow().setWindowed(1920,1080);c.resizeGui();next();}}
            case 7 -> {if(ticks==20){press(c,"storages.locate");check(c.gui.screen()==null&&StorageLocator.target()!=null,"locate button closes menu and targets chest");locatedKey=StorageLocator.target();var target=TrackedStorageStore.get(locatedKey);var p=target.positions().getFirst();double dx=p.getX()+.5-c.player.getX(),dz=p.getZ()+.5-c.player.getZ(),dy=p.getY()+.5-c.player.getEyeY();c.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));c.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));next();}}
            case 8 -> {if(ticks==20){shot(c,"tracked-storage-highlight.png");check(StorageLocator.target()!=null,"world renderer retains valid highlight");var p=TrackedStorageStore.get(locatedKey).positions().getFirst();c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p),net.minecraft.core.Direction.UP,p,false));next();}}
            case 9 -> {var opening=ContainerRecorder.active();if(opening!=null&&opening.trackingKey.equals(locatedKey)&&opening.synchronizedMenu&&ticks>12){check(StorageLocator.target()==null&&StorageLocator.opacity(System.nanoTime())==0,"opening the actual located server chest dismisses and finishes fading the outline");shot(c,"tracked-storage-opened.png");c.player.closeContainer();check(ResourceAccounting.available(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE),list)==448,"bound schematic resource count remains correct through menus");log.add("STORAGE UI LIVE PASS: typed group creation, label editing, membership, filters, per-file binding, item-content search, 320x240 controls, real Locate click, world highlight and dismissal after opening the actual target");c.setScreenAndShow(new ResourceSettingsScreen(new SettingsScreen(null)));return true;}}
        }return false;
    }
    private StorageUiChecks(){}
}
