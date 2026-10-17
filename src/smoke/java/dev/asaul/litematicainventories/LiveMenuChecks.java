package dev.asaul.litematicainventories;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.*;
import fi.dy.masa.litematica.schematic.*;
import fi.dy.masa.litematica.schematic.placement.*;
import fi.dy.masa.litematica.scheduler.*;
import fi.dy.masa.litematica.scheduler.tasks.*;
import fi.dy.masa.litematica.materials.*;
import java.nio.file.*;
import java.util.*;
public final class LiveMenuChecks {
 static boolean started;
 private static int stage,ticks;private static BlockPos pos;private static List<String> log;
 private static String reopenWorld;
 private static HudRenderChecks hudChecks;
 private static CacheKey key;private static LitematicaSchematic portable;private static SchematicPlacement placement;
 static void start(Minecraft c,List<String> messages) {
  StorageSettings.INSTANCE.trackingPositions.keySet().removeIf(k->k.endsWith(".header-v2"));
  started=true;c.options.pauseOnLostFocus=false;c.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);log=messages;stage=0;ticks=0;pos=c.player.blockPosition().offset(2,0,0);
  key=new CacheKey(SessionContext.serverId(c),SessionContext.dimensionId(c),pos);
  StorageSettings.INSTANCE.recording=true;StorageSettings.INSTANCE.resourceTracking=true;StorageSettings.INSTANCE.includeTrackedResources=false;StorageSettings.INSTANCE.showTrackingButton=true;StorageSettings.arrangeTrackingButton=false;
  c.setScreenAndShow(null);
  c.getSingleplayerServer().execute(()->{var w=c.getSingleplayerServer().overworld();w.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);w.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);w.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);var be=(ChestBlockEntity)w.getBlockEntity(pos);be.setItem(0,new ItemStack(Items.DIAMOND,32));be.setChanged();});
 }
 private static void next(int s){System.out.println("LIVE stage "+stage+" -> "+s);stage=s;ticks=0;}
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 private static int count(ContainerSnapshot s,Minecraft c){return InventoryData.decode(s.blockEntityData(),27,c.level.registryAccess()).getFirst().getCount();}
 private static void screenshot(Minecraft c,String name){net.minecraft.client.Screenshot.grab(new java.io.File("."),name,c.gameRenderer.mainRenderTarget(),1,msg->{});}
 private static void visualFixture(Minecraft c,boolean enable){
  var expected=new ArrayList<ItemStack>(Collections.nCopies(27,ItemStack.EMPTY));expected.set(0,new ItemStack(Items.DIAMOND,32));
  if(enable){expected.set(1,new ItemStack(Items.REDSTONE,16));expected.set(2,new ItemStack(Items.REDSTONE,16));expected.set(4,new ItemStack(Items.REDSTONE,16));expected.set(6,new ItemStack(Items.REDSTONE,16));}
  var map=portable.getBlockEntityMapForRegion("storage");
  var data=fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt.toVanillaCompound(map.get(BlockPos.ZERO));
  data.put("Items",InventoryData.encode(expected,c.level.registryAccess()).get("Items"));
  map.put(BlockPos.ZERO,fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt.fromVanillaCompound(data));
  c.getSingleplayerServer().execute(()->{var be=(ChestBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);for(int n=1;n<27;n++)be.setItem(n,ItemStack.EMPTY);if(enable){be.setItem(1,new ItemStack(Items.REDSTONE_TORCH));be.setItem(2,new ItemStack(Items.REDSTONE,8));be.setItem(3,new ItemStack(Items.REDSTONE,5));be.setItem(4,new ItemStack(Items.REDSTONE,16));be.setItem(6,new ItemStack(Items.REDSTONE,24));}be.setChanged();});
 }
 private static void materialFixture(Minecraft c){
  Item[] types={Items.DIAMOND,Items.REDSTONE,Items.ARROW,Items.BONE,Items.GLASS_BOTTLE,Items.GLOWSTONE_DUST,Items.GUNPOWDER,Items.STRING,Items.WATER_BUCKET,Items.WHEAT_SEEDS,Items.STONE,Items.COBBLESTONE,Items.OAK_LOG,Items.IRON_INGOT,Items.GOLD_INGOT,Items.COAL,Items.EMERALD,Items.LAPIS_LAZULI,Items.COPPER_INGOT,Items.OBSIDIAN,Items.SAND,Items.GRAVEL,Items.DIRT,Items.TORCH,Items.CHEST,Items.HOPPER,Items.PISTON};
  var expected=new ArrayList<ItemStack>();for(int n=0;n<27;n++)expected.add(new ItemStack(types[n],n==8?1:n+1));
  var map=portable.getBlockEntityMapForRegion("storage");var data=fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt.toVanillaCompound(map.get(BlockPos.ZERO));data.put("Items",InventoryData.encode(expected,c.level.registryAccess()).get("Items"));map.put(BlockPos.ZERO,fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt.fromVanillaCompound(data));
 }
 private static net.minecraft.client.input.MouseButtonEvent click(double x,double y){return new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));}
 private static Object field(Object owner,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 private static void nativeUiChecks(Minecraft c)throws Exception{
  var screen=(StorageMaterialScreen)c.gui.screen();
  var buttons=((dev.asaul.litematicainventories.mixin.GuiBaseButtonsAccess)(Object)screen).storage$buttons();
  String tab=net.minecraft.network.chat.Component.translatable("storage.tab").getString();
  check(buttons.stream().noneMatch(b->((String)uncheckedField(b,fi.dy.masa.malilib.gui.button.ButtonBase.class,"displayString")).equals(tab)),"no inert In storages button");
  var scrollbar=(fi.dy.masa.malilib.gui.GuiScrollBar)field(screen,StorageMaterialScreen.class,"scrollbar");
  check(scrollbar.getMaxValue()>0,"native scrollbar has material overflow");
  int table=(int)field(screen,StorageMaterialScreen.class,"tableY");
  screen.onMouseScrolled(50,table+25,0,-1);check(scrollbar.getValue()==3,"mouse wheel scrolls rows");
  var headers=(List<fi.dy.masa.malilib.gui.button.ButtonGeneric>)field(screen,StorageMaterialScreen.class,"headers");var required=headers.get(1);
  screen.onMouseClicked(click(required.getX()+4,required.getY()+4),false);
  check((int)field(screen,StorageMaterialScreen.class,"sort")==1&&scrollbar.getValue()==0,"native header click changes sort and resets scroll");
  var rows=(List<StorageMaterialList.Row>)field(screen,StorageMaterialScreen.class,"visible");check(rows.getFirst().expected()>=rows.getLast().expected(),"required sort descending");
  var wrappers=(List<fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper<?>>)field(screen,fi.dy.masa.malilib.gui.GuiBase.class,"textFields");var input=wrappers.getFirst().textField();
  screen.onMouseClicked(click(input.getX()+4,input.getY()+4),false);String query=new ItemStack(Items.DIAMOND).getHoverName().getString();
  for(int cp:query.codePoints().toArray())screen.onCharTyped(new net.minecraft.client.input.CharacterEvent(cp));
  rows=(List<StorageMaterialList.Row>)field(screen,StorageMaterialScreen.class,"visible");check(rows.size()==1&&rows.getFirst().key().stack().is(Items.DIAMOND),"native search input filters item rows");
  input.setValueWrapper("");((fi.dy.masa.malilib.gui.interfaces.ITextFieldListener)wrappers.getFirst().listener()).onTextChange(input);
  log.add("LIVE PASS: native malilib screen, toolbar navigation, no inert tab, scrollbar wheel, clickable sort and real typed search");
 }
 private static Object uncheckedField(Object owner,Class<?> type,String name){try{return field(owner,type,name);}catch(Exception e){throw new RuntimeException(e);}}
 private static TrackingButton tracking(Minecraft c){return c.gui.screen().children().stream().filter(w->w instanceof TrackingButton).map(w->(TrackingButton)w).findFirst().orElseThrow();}
 private static void trackClick(Minecraft c){var b=tracking(c);var event=click(b.getX()+11,b.getY()+11);c.gui.screen().mouseClicked(event,false);c.gui.screen().mouseReleased(event);}
 private static int resourceFirst(Minecraft c){var o=ContainerRecorder.active();var e=TrackedStorageStore.snapshot().stream().filter(s->s.key().equals(TrackedStorageStore.key(o))).findFirst().orElseThrow();return InventoryData.decode(e.data(),e.size(),c.level.registryAccess()).getFirst().getCount();}
 private static void nativeClick(Minecraft c,String key){var gui=(fi.dy.masa.malilib.gui.GuiBase)c.gui.screen();var button=((dev.asaul.litematicainventories.mixin.GuiBaseButtonsAccess)gui).storage$buttons().stream().filter(b->uncheckedField(b,fi.dy.masa.malilib.gui.button.ButtonBase.class,"displayString").equals(net.minecraft.network.chat.Component.translatable(key).getString())).findFirst().orElseThrow();gui.onMouseClicked(click(button.getX()+4,button.getY()+4),false);}
 private static void confirm(Minecraft c,boolean yes){var buttons=c.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).toList();var b=buttons.get(yes?0:1);c.gui.screen().mouseClicked(click(b.getX()+4,b.getY()+4),false);}
 private static void open(Minecraft c){c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
 static boolean tick(Minecraft c)throws Exception {
  if(++ticks>800)throw new AssertionError("Live menu timeout at stage "+stage+"; active="+ContainerRecorder.active()+"; screen="+c.gui.screen());
  switch(stage){
   case 0 -> {if(c.level.getBlockState(pos).is(Blocks.CHEST)&&ticks>10){
    check(SessionInventoryCache.get(key)==null,"never opened chest remains unknown");
    c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));next(1);
   }}
   case 1 -> {if(ticks%40==0 && c.gui.screen()==null)c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));var o=ContainerRecorder.active();var cached=SessionInventoryCache.get(key);if(o!=null&&o.synchronizedMenu&&cached!=null&&tracking(c).active&&ticks>10){
    check(count(cached,c)==32,"server full content captured");
    trackClick(c);check(TrackedStorageStore.tracked(o)&&resourceFirst(c)==32,"real GUI button registers synchronized chest");
    var b=tracking(c);StorageSettings.arrangeTrackingButton=false;double sx=b.getX()+11,sy=b.getY()+11;double targetX=sx-18;
    c.gui.screen().mouseClicked(click(sx,sy),false);c.gui.screen().mouseDragged(click(targetX,sy),targetX-sx,0);c.gui.screen().mouseReleased(click(targetX,sy));StorageSettings.arrangeTrackingButton=false;
    check(!StorageSettings.INSTANCE.trackingPositions.isEmpty()&&TrackedStorageStore.tracked(o),"direct dragging saves position without untracking");
    var hold=click(b.getX()+8,b.getY()+7);c.gui.screen().mouseClicked(hold,false);
    var timer=TrackingButton.class.getDeclaredField("pressedAt");timer.setAccessible(true);timer.setLong(b,System.nanoTime()-400_000_000L);
    c.gui.screen().mouseReleased(hold);check(TrackedStorageStore.tracked(o),"long hold without moving never toggles tracking");
    var jitter=click(b.getX()+8,b.getY()+7);c.gui.screen().mouseClicked(jitter,false);c.gui.screen().mouseDragged(click(jitter.x()+1,jitter.y()+1),1,1);c.gui.screen().mouseReleased(jitter);
    check(!TrackedStorageStore.tracked(o),"one-pixel jitter remains a short click");trackClick(c);check(TrackedStorageStore.tracked(o),"short click restores tracking after jitter test");
    log.add("RESOURCE LIVE PASS: actual icon click registers chest; direct drag persists position without toggling");log.add("LIVE PASS: survival use-block Р В Р вЂ Р Р†Р вЂљР’В Р Р†Р вЂљРІвЂћСћ synchronized menu Р В Р вЂ Р Р†Р вЂљР’В Р Р†Р вЂљРІвЂћСћ persistent cache (32 diamonds)");
    AreaSelection area=new AreaSelection();area.setExplicitOrigin(pos);area.addSubRegionBox(new Box(pos,pos,"storage"),false);
    Files.deleteIfExists(Path.of("test-schematics/live-survival.litematic"));
    var s=LitematicaSchematic.createEmptySchematic(area,"live-survival");
    var task=new TaskSaveSchematic(Path.of("test-schematics"),"live-survival",s,area,new LitematicaSchematic.SchematicSaveInfo(false,false,true,false),true);
    TaskScheduler.getInstanceClient().scheduleTask(task,1);
    StorageSettings.INSTANCE.recording=false;ContainerRecorder.recordingChanged();
    c.getSingleplayerServer().execute(()->{var be=(ChestBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);be.setItem(0,new ItemStack(Items.DIAMOND,16));be.setChanged();});next(2);
   }}
   case 2 -> {var o=ContainerRecorder.active();if(o!=null&&o.adapter.copyItems().getFirst().getCount()==16&&Files.exists(Path.of("test-schematics/live-survival.litematic"))){
    check(count(SessionInventoryCache.get(key),c)==32,"recording off retains old data");
    check(resourceFirst(c)==16,"resource snapshot updates from real server while schematic recording OFF");
    portable=LitematicaSchematic.createFromFile(Path.of("test-schematics"),"live-survival.litematic");
    var data=fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt.toVanillaCompound(portable.getBlockEntityMapForRegion("storage").get(BlockPos.ZERO));
    check(InventoryData.decode(data,27,c.level.registryAccess()).getFirst().getCount()==32,"actual save task uses inventory snapshot");
    log.add("LIVE PASS: recording OFF stops updates; TaskSaveSchematic wrote portable 32-item snapshot after target changed to 16");
    StorageSettings.INSTANCE.recording=true;ContainerRecorder.recordingChanged();
    c.getSingleplayerServer().execute(()->{var be=(ChestBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);be.setItem(0,ItemStack.EMPTY);be.setChanged();});next(3);
   }}
   case 3 -> {var o=ContainerRecorder.active();if(o!=null&&o.adapter.copyItems().getFirst().isEmpty()&&count(SessionInventoryCache.get(key),c)==0){
    log.add("LIVE PASS: recording ON observes server slot update to known empty");
    placement=SchematicPlacement.createTemporary(portable,pos);var manager=DataManager.getSchematicPlacementManager();manager.addSchematicPlacement(placement,false);manager.setSelectedSchematicPlacement(placement);
    var model=new StorageMaterialList(new MaterialListPlacement(placement,false));model.refresh();
    check(model.expectedKnown==1&&model.actualKnown==1,"material list coverage");check(model.rows.size()==1&&model.rows.getFirst().expected()==32&&model.rows.getFirst().missing()==32,"material list container deficit");
    log.add("LIVE PASS: storage material tab separates expected 32 / observed 0 / missing 32");
    visualFixture(c,true);StorageSettings.INSTANCE.aggregate=false;c.options.guiScale().set(2);c.resizeGui();next(4);
   }}
   case 4 -> {
    if(ticks==140){screenshot(c,"storage-slot-colors.png");var slots=ContainerRecorder.active().adapter.slots();
     check(StorageOverlayRenderer.tooltip(slots.get(4)).isEmpty(),"matching item has no extra tooltip");
     check(StorageOverlayRenderer.tooltip(slots.get(5)).isEmpty(),"empty match has no tooltip");
     check(StorageOverlayRenderer.tooltip(slots.get(1)).size()==1,"wrong item adds only expected line");
     check(StorageOverlayRenderer.tooltip(slots.get(2)).size()==2,"quantity mismatch adds two short lines");
     check(StorageOverlayRenderer.tooltip(slots.get(3)).size()==1,"extra item adds one line");
     log.add("LIVE PASS: compact tooltips: match/empty 0 lines; wrong/extra 1 line; quantity 2 lines");
     double scale=c.getWindow().getGuiScale();var slot=slots.get(1);
     c.mouseHandler.onMove(c.getWindow().handle(),((c.getWindow().getGuiScaledWidth()-176)/2+slot.x+8)*scale,((c.getWindow().getGuiScaledHeight()-166)/2+slot.y+8)*scale,0,0);
    }
    if(ticks==160){screenshot(c,"storage-overlay-scale2.png");StorageSettings.INSTANCE.aggregate=true;next(5);}}
   case 5 -> {if(ticks==30){screenshot(c,"storage-overlay-aggregate.png");c.getWindow().setWindowed(1280,800);c.options.guiScale().set(3);c.resizeGui();next(6);}}
   case 6 -> {if(ticks==30){screenshot(c,"storage-overlay-scale3.png");visualFixture(c,false);
    net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_F10));next(60);
   }}
   case 60 -> {if(c.gui.screen() instanceof SettingsScreen && ticks>5){check(c.player.containerMenu==c.player.inventoryMenu,"F10 closes real menu cleanly");log.add("LIVE PASS: F10 settings closes the server menu cleanly and retains clear-container context");c.getWindow().setWindowed(1920,1080);c.options.guiScale().set(2);c.resizeGui();var original=new fi.dy.masa.litematica.gui.GuiMaterialList(new MaterialListPlacement(placement,false));c.setScreenAndShow(original);next(7);}}
   case 7 -> {if(ticks==30){screenshot(c,"material-list-tab-button.png");materialFixture(c);var parent=(fi.dy.masa.litematica.gui.GuiMaterialList)c.gui.screen();
    var button=((dev.asaul.litematicainventories.mixin.GuiBaseButtonsAccess)parent).storage$buttons().stream().filter(b->((String)uncheckedField(b,fi.dy.masa.malilib.gui.button.ButtonBase.class,"displayString")).equals(net.minecraft.network.chat.Component.translatable("storage.tab").getString())).findFirst().orElseThrow();
    check(button.getY()>=24,"entry button is in toolbar below title");parent.onMouseClicked(click(button.getX()+4,button.getY()+4),false);check(c.gui.screen() instanceof StorageMaterialScreen,"native toolbar button opens storage list");next(8);}}
   case 8 -> {if(ticks==30){nativeUiChecks(c);screenshot(c,"storage-material-list.png");visualFixture(c,false);c.setScreenAndShow(new SettingsScreen(c.gui.screen()));next(9);}}
   case 9 -> {if(ticks==30){screenshot(c,"storage-settings.png");SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();check(PersistentInventoryStore.load().containsKey(key),"live contents persisted");log.add("LIVE PASS: container and material screens rendered at GUI scales 2 and 3; screenshots saved (visual review separate)");
    c.setScreenAndShow(null);c.getSingleplayerServer().execute(()->{var be=(ChestBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);be.setItem(0,new ItemStack(Items.DIAMOND,32));be.setChanged();});next(10);}}
   case 10 -> {if(ticks==30){open(c);next(11);}}
   case 11 -> {var o=ContainerRecorder.active();if(o!=null&&o.synchronizedMenu&&o.adapter.copyItems().getFirst().getCount()==32){
    c.gameMode.handleContainerInput(o.menu.containerId,0,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,c.player);next(12);
   }}
   case 12 -> {var o=ContainerRecorder.active();if(o!=null&&o.adapter.copyItems().getFirst().isEmpty()&&count(SessionInventoryCache.get(key),c)==0){
    check(c.player.getInventory().getNonEquipmentItems().stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum()>=32,"vanilla shift click moved items");
    log.add("LIVE PASS: vanilla shift-click with overlay moved 32 diamonds and recorder observed the changed synchronized menu");
    ResourceAccounting.tick(c);check(resourceFirst(c)==0&&ResourceAccounting.available(new ItemStack(Items.DIAMOND))==32,"transfer counts 32 once across player and chest");
    log.add("RESOURCE LIVE PASS: vanilla shift-click conserves total without duplicate player/chest resources");
    c.player.closeContainer();c.setScreenAndShow(new ResourceSettingsScreen(new SettingsScreen(null)));next(120);
   }}
   case 120 -> {if(ticks==15){nativeClick(c,"storages.clear");check(c.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen,"clear requires confirmation");next(121);}}
   case 121 -> {if(ticks==10){confirm(c,false);check(!TrackedStorageStore.snapshot().isEmpty(),"cancel preserves tracked data");nativeClick(c,"storages.clear");next(122);}}
   case 122 -> {if(ticks==10){confirm(c,true);check(TrackedStorageStore.snapshot().isEmpty()&&SessionInventoryCache.get(key)!=null,"confirmed clear leaves schematic cache intact");
    log.add("RESOURCE LIVE PASS: native clear confirmation/cancel works; schematic cache remains independent");
    c.setScreenAndShow(null);c.getSingleplayerServer().execute(()->{var be=(ChestBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);for(int n=0;n<27;n++)be.setItem(n,n<5?new ItemStack(Items.STONE,64):ItemStack.EMPTY);be.setChanged();var p=c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());p.getInventory().setItem(20,new ItemStack(Items.STONE,64));p.getInventory().setItem(21,new ItemStack(Items.STONE,64));});next(123);
   }}
   case 123 -> {if(ticks==30){open(c);next(1231);}}
   case 1231 -> {var o=ContainerRecorder.active();if(o!=null&&o.synchronizedMenu&&tracking(c).active&&o.adapter.copyItems().stream().filter(s->s.is(Items.STONE)).mapToInt(ItemStack::getCount).sum()==320){
    trackClick(c);StorageSettings.INSTANCE.includeTrackedResources=true;StorageSettings.save();ResourceAccounting.tick(c);check(ResourceAccounting.available(new ItemStack(Items.STONE))==448,"real server chest320 plus player128");
    c.player.closeContainer();var custom=new MaterialListCustom("Storages+ 640 / 448",Map.of(new fi.dy.masa.malilib.util.data.ItemType(new ItemStack(Items.STONE),true,false),640),Path.of("resource-example.json"));
    c.setScreenAndShow(new fi.dy.masa.litematica.gui.GuiMaterialList(custom));var p=ResourceAccounting.progress(custom);check(p.collected()==448&&p.remaining()==192&&p.percent()==70,"real Material List example progress");next(124);
   }}
   case 124 -> {if(ticks==30){screenshot(c,"resource-material-list.png");nativeClick(c,"storages.mode.true");check(!StorageSettings.INSTANCE.includeTrackedResources,"native mode button switches to inventory");nativeClick(c,"storages.mode.false");check(StorageSettings.INSTANCE.includeTrackedResources,"native mode button restores combined mode");hudChecks=new HudRenderChecks(((fi.dy.masa.litematica.gui.GuiMaterialList)c.gui.screen()).getMaterialList());c.setScreenAndShow(hudChecks);next(1241);}}
   case 1241 -> {if(ticks==15){hudChecks.expect(192);screenshot(c,"resource-info-hud.png");StorageSettings.INSTANCE.includeTrackedResources=false;next(1242);}}
   case 1242 -> {if(ticks==15){hudChecks.expect(512);StorageSettings.INSTANCE.includeTrackedResources=true;hudChecks.list.setMultiplier(2);next(1243);}}
   case 1243 -> {if(ticks==15){hudChecks.expect(832);hudChecks.list.setMultiplier(1);hudChecks.list.setMaterialListEntries(List.of(new MaterialListEntry(new ItemStack(Items.STONE),448,448,0,128)));next(1244);}}
   case 1244 -> {if(ticks==15){check(hudChecks.renderedHeight==0&&hudChecks.hud.counts.isEmpty(),"fully covered HUD row disappears");hudChecks.list.setMaterialListEntries(List.of(new MaterialListEntry(new ItemStack(Items.STONE),640,640,0,128)));hudChecks.list.ignoreEntry(hudChecks.list.getMaterialsAll().getFirst());next(1245);}}
   case 1245 -> {if(ticks==15){check(hudChecks.renderedHeight==0&&hudChecks.hud.counts.isEmpty(),"ignored HUD row stays hidden");log.add("HUD LIVE PASS: native renderer width/draw passes: combined192, inventory512, multiplier2=832; fully covered and ignored rows hidden without mutating inventory counts");c.setScreenAndShow(new ResourceSettingsScreen(new SettingsScreen(null)));next(125);}}
   case 125 -> {if(ticks==25){screenshot(c,"resource-settings.png");TrackedResourceChecks.persistRestartProof();reopenWorld=c.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();c.disconnect(new net.minecraft.client.gui.screens.TitleScreen(),false);next(126);}}
   case 126 -> {if(c.level==null&&ticks>20){c.createWorldOpenFlows().openWorld(reopenWorld,()->{});next(127);}}
   case 127 -> {
    if(c.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen&&ticks%10==0){
     var button=c.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).filter(b->b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString())).findFirst().orElseThrow();c.gui.screen().mouseClicked(click(button.getX()+4,button.getY()+4),false);
    }
    if(c.level!=null&&c.player!=null&&c.level.hasChunkAt(pos)&&c.level.getBlockState(pos).is(Blocks.CHEST)&&ticks>30){ResourceAccounting.tick(c);check(ResourceAccounting.available(new ItemStack(Items.STONE))==448,"same world reconnect preserves resources and identity");open(c);next(128);}}
   case 128 -> {if(ticks%40==0&&c.gui.screen()==null)open(c);var o=ContainerRecorder.active();if(o!=null&&o.synchronizedMenu){check(TrackedStorageStore.tracked(o),"tracked status survives reconnect");StorageSettings.INSTANCE.recording=false;ContainerRecorder.recordingChanged();c.getSingleplayerServer().execute(()->{var be=(ChestBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);be.setItem(0,new ItemStack(Items.STONE,63));be.setChanged();});next(129);}}
   case 129 -> {var o=ContainerRecorder.active();if(o!=null&&o.adapter.copyItems().getFirst().getCount()==63&&resourceFirst(c)==63){ResourceAccounting.tick(c);check(ResourceAccounting.available(new ItemStack(Items.STONE))==447,"reopened server update while recording OFF is independent");TrackedResourceChecks.persistRestartProof();
    log.add("RESOURCE LIVE PASS: real 640/448/192/70% Material List, mode buttons, disconnect/reconnect, reopen update with recording OFF, persisted restart witness");
    c.player.closeContainer();ShulkerDistanceChecks.start(c);next(130);
   }}
   case 130 -> {if(ShulkerDistanceChecks.tick(c,log)){TrackedResourceChecks.persistRestartProof();SessionInventoryCache.clear();check(!TrackedStorageStore.snapshot().isEmpty(),"F9-style cache clear retains permanent ledger");c.disconnect(new net.minecraft.client.gui.screens.TitleScreen(),false);next(13);}}
   case 13 -> {if(c.level==null&&ticks>20){
    var model=new StorageMaterialList(new MaterialListSchematic(portable,false));model.refresh();
    check(model.rawOffline&&model.expectedKnown==1&&model.offlineRows.size()==1&&model.offlineRows.getFirst().expected()==32,"offline portable expected inventory without cache or bound world");
    log.add("LIVE PASS: offline schematic required count 32 remains available after disconnect and cache clear");
    c.setScreenAndShow(new StorageMaterialScreen(new net.minecraft.client.gui.screens.TitleScreen(),model));next(14);
   }}
   case 14 -> {if(ticks==30){screenshot(c,"storage-offline.png");
    var empty=new CompoundTag();empty.put("Items",new ListTag());
    SessionInventoryCache.put(new CacheKey("restart-proof","test:dim",BlockPos.ZERO),new ContainerSnapshot("minecraft:chest",Blocks.CHEST.defaultBlockState().toString(),27,System.currentTimeMillis(),net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),empty));
    SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();c.getWindow().setWindowed(640,480);c.options.guiScale().set(2);c.resizeGui();next(15);
   }}
   case 15 -> {if(ticks==30){screenshot(c,"storage-narrow-window.png");log.add("LIVE PASS: storage table rendered at 320x240 GUI size without overlapping top controls or numeric columns");return true;}}
  }
  return false;
 }
}

