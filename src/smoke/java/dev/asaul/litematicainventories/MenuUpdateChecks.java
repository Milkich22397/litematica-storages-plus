package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import fi.dy.masa.malilib.gui.*;
import fi.dy.masa.malilib.gui.button.*;
import java.util.*;
public final class MenuUpdateChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static MouseButtonEvent click(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));}
    static void press(Minecraft c,String id){var gui=(StorageDashboard)c.gui.screen();var b=gui.controls.get(id);if(b==null)throw new AssertionError("Missing dashboard control "+id);gui.mouseClicked(click(b.getX()+3,b.getY()+3),false);}
    private static void cancel(Minecraft c){check(c.gui.screen() instanceof ConfirmScreen,"destructive action opens confirmation");var b=c.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).toList().get(1);c.gui.screen().mouseClicked(click(b.getX()+3,b.getY()+3),false);}
    private static Object field(Object obj,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    @SuppressWarnings("unchecked")
    private static void input(Minecraft c,String key,String value)throws Exception{
        var gui=(StorageDashboard)c.gui.screen();var inputs=(Map<String,GuiTextFieldGeneric>)field(gui,StorageDashboard.class,"inputs");var target=inputs.get(key);
        var wrappers=(List<fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper<?>>)field(gui,GuiBase.class,"textFields");var wrapper=wrappers.stream().filter(w->w.textField()==target).findFirst().orElseThrow();target.setValueWrapper(value);((fi.dy.masa.malilib.gui.interfaces.ITextFieldListener)wrapper.listener()).onTextChange(target);
    }
    @SuppressWarnings("unchecked")
    public static void run(Minecraft c,List<String> log)throws Exception{
        var original=c.gui.screen();int cache=SessionInventoryCache.size(),tracked=TrackedStorageStore.snapshot().size();var saved=StorageSettings.INSTANCE;String savedJson=new com.google.gson.Gson().toJson(saved);
        try{
            var gui=new SettingsScreen(null);c.setScreenAndShow(gui);check(gui.tab==0&&gui.railX>gui.leftRight&&gui.railWidth<gui.leftRight,"default storages tab with narrower sidebar");
            press(c,"tab.1");check(c.gui.screen()==gui&&gui.tab==1,"tabs retain one screen");
            input(c,"missingColor","12345678");check(StorageSettings.INSTANCE.missingColor==0x12345678,"inline color input updates preference");
            check(((String)field(gui,StorageDashboard.class,"infoBody")).contains("AARRGGBB"),"selected color explains format in sidebar");
            int countColor=StorageSettings.INSTANCE.countColor;press(c,"reset");check(StorageSettings.INSTANCE.missingColor==new StorageSettings().missingColor&&StorageSettings.INSTANCE.countColor==countColor,"reset only selected preference");
            input(c,"missingColor","12345678");boolean recording=StorageSettings.INSTANCE.recording,overlay=StorageSettings.INSTANCE.overlay;press(c,"recording");press(c,"overlay");press(c,"cancel");
            check(c.gui.screen()==null&&new com.google.gson.Gson().toJson(StorageSettings.INSTANCE).equals(savedJson),"Cancel restores preference snapshot including recording and overlay");
            gui=new SettingsScreen(null);c.setScreenAndShow(gui);press(c,"tab.1");input(c,"missingColor","23456789");press(c,"done");check(c.gui.screen()==null&&StorageSettings.INSTANCE.missingColor==0x23456789,"Done commits preferences");
            var disk=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(StorageSettings.DIRECTORY.resolve("settings.json")),StorageSettings.class);check(disk.missingColor==0x23456789,"Done writes preference file");
            c.setScreenAndShow(new SettingsScreen(null));press(c,"tab.2");
            for(String action:List.of("storage.clear.container","storage.clear.world","storage.clear.all","storages.clear","storage.cleanup")){press(c,action);cancel(c);check(SessionInventoryCache.size()==cache&&TrackedStorageStore.snapshot().size()==tracked,"cancel confirmed action preserves data");}
            input(c,"cleanupHours","0");var cleanup=((StorageDashboard)c.gui.screen()).controls.get("storage.cleanup");check(!(Boolean)field(cleanup,ButtonBase.class,"enabled"),"invalid cleanup age disables deletion");
            press(c,"tab.1");check(!(Boolean)field(((StorageDashboard)c.gui.screen()).controls.get("done"),ButtonBase.class,"enabled"),"invalid draft blocks saving after changing tabs");press(c,"tab.2");var currentInputs=(Map<String,GuiTextFieldGeneric>)field(c.gui.screen(),StorageDashboard.class,"inputs");check(currentInputs.get("cleanupHours").getValueWrapper().equals("0"),"invalid draft remains visible when returning to tab");cleanup=((StorageDashboard)c.gui.screen()).controls.get("storage.cleanup");
            input(c,"cleanupHours","168");check((Boolean)field(cleanup,ButtonBase.class,"enabled"),"valid cleanup age enables confirmation");
            press(c,"tab.1");var wrappers=(List<fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper<?>>)field(c.gui.screen(),GuiBase.class,"textFields");var wrapper=wrappers.getFirst();var search=wrapper.textField();search.setValueWrapper("opacity");((fi.dy.masa.malilib.gui.interfaces.ITextFieldListener)wrapper.listener()).onTextChange(search);c.gui.screen().tick();
            var dashboard=(StorageDashboard)c.gui.screen();check((int)field(dashboard,StorageDashboard.class,"optionCount")==5,"settings search finds color descriptions");
            press(c,"tab.2");check(dashboard.query.isEmpty(),"searches are independent per tab");press(c,"tab.1");check(dashboard.query.equals("opacity"),"tab search survives round trip");
            press(c,"cancel");
            var escapeScreen=new SettingsScreen(null);c.setScreenAndShow(escapeScreen);press(c,"tab.1");boolean oldOverlay=StorageSettings.INSTANCE.overlay;press(c,"overlay");escapeScreen.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE,0,0));check(c.gui.screen()==null&&StorageSettings.INSTANCE.overlay==oldOverlay,"Escape cancels preference edits");
            log.add("MENU UPDATE PASS: unified tabs, narrow sidebar, contextual settings search and details, color editing, selected reset, Done persistence, Cancel rollback, deletion confirmations and invalid-age protection");
        }finally{StorageSettings.INSTANCE=new com.google.gson.Gson().fromJson(savedJson,StorageSettings.class);StorageSettings.save();ResourceAccounting.tick(c);c.setScreenAndShow(original);}
    }
}
