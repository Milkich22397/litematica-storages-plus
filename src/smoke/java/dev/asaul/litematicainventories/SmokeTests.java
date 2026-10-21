package dev.asaul.litematicainventories;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import fi.dy.masa.litematica.schematic.*;
import fi.dy.masa.litematica.schematic.placement.*;
import fi.dy.masa.litematica.selection.*;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import java.nio.file.*;
import java.util.*;

public final class SmokeTests implements ClientModInitializer {
 private static int checks;
 private static boolean ran,worldStarted;
 private static final List<String> log=new ArrayList<>();
 private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{
  if(!worldStarted&&c.gui.screen() instanceof TitleScreen){worldStarted=true;c.createWorldOpenFlows().createFreshLevel("storage-preview-smoke-"+System.currentTimeMillis(),new net.minecraft.world.level.LevelSettings("Storage Preview integration",net.minecraft.world.level.GameType.SURVIVAL,new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),false,net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(12831L,false,false),net.minecraft.world.level.levelgen.presets.WorldPresets::createTestWorldDimensions,new TitleScreen());}
  if(!ran&&c.level!=null&&c.player!=null&&c.level.getGameTime()>40){ran=true;try{run();TrackedResourceChecks.run(c,log);OptimizationChecks.run(c,log);StorageFeatureChecks.run(c,log);log.add("SMOKE PASS: "+checks+" assertions");LiveMenuChecks.start(c,log);}catch(Throwable e){finish(c,e);}}
  else if(ran&&LiveMenuChecks.started){try{if(LiveMenuChecks.tick(c))finish(c,null);}catch(Throwable e){finish(c,e);}}
 });}
 private static void finish(Minecraft c,Throwable error){
  LiveMenuChecks.started=false;if(error!=null){error.printStackTrace();log.add("SMOKE FAIL: "+error);}
  try{Files.write(Path.of("smoke-result.txt"),log);}catch(Exception e){throw new RuntimeException(e);}c.stop();
 }
 private static void run() throws Exception {
  String binary=System.getProperty("storages.binaryUnderTest");
  if(binary!=null){var actual=Path.of(ContainerRecorder.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();check(actual.equals(Path.of(binary).toRealPath()),"integration loads the exact distributable JAR rather than development classes");log.add("BINARY PASS: production classes loaded from "+actual.getFileName());}
  // Force all mandatory hooks to transform, including classes not normally loaded by title screen.
  for(String name:List.of("fi.dy.masa.litematica.scheduler.tasks.TaskSaveSchematic","fi.dy.masa.litematica.gui.GuiMaterialList","fi.dy.masa.litematica.materials.MaterialListSchematic","fi.dy.masa.litematica.materials.MaterialListPlacement","net.minecraft.client.gui.screens.inventory.AbstractContainerScreen"))Class.forName(name);
  log.add("Mandatory mixin target classes loaded");
  var legacySettings=new com.google.gson.Gson().fromJson("{\"aggregate\":true,\"recording\":true,\"overlay\":false,\"namespace\":\"legacy-slot-only\"}",StorageSettings.class);
  check(legacySettings.recording&&!legacySettings.overlay&&legacySettings.namespace.equals("legacy-slot-only"),"legacy whole-container setting is ignored without losing other preferences");
  check(!new com.google.gson.Gson().toJsonTree(legacySettings).getAsJsonObject().has("aggregate"),"obsolete comparison setting is not saved again");
  check(Arrays.stream(Minecraft.getInstance().options.keyMappings).noneMatch(k->k.getName().equals("key.litematicainventories.toggle_mode")),"comparison-mode key binding no longer registered");
  log.add("SLOT-ONLY PASS: legacy aggregate=true ignored, other settings retained, obsolete mode not saved and F7 toggle removed");
  var reg=Minecraft.getInstance().level.registryAccess();
  if(SessionInventoryCache.snapshot().containsKey(new CacheKey("restart-proof","test:dim",BlockPos.ZERO)))log.add("RESTART PASS: prior process persisted known-empty marker and new client loaded it");
  ItemStack sword=new ItemStack(Items.DIAMOND_SWORD);ItemStack damaged=sword.copy();damaged.set(DataComponents.DAMAGE,7);
  check(InventoryComparator.strict(sword,damaged)==ComparisonEngine.Status.WRONG,"damage identity");
  ItemStack enchanted=sword.copy();enchanted.enchant(reg.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),2);
  check(InventoryComparator.strict(sword,enchanted)==ComparisonEngine.Status.WRONG,"enchantment identity");
  ItemStack healing=net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.HEALING);
  ItemStack poison=net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.POISON);
  check(InventoryComparator.strict(healing,poison)==ComparisonEngine.Status.WRONG,"potion identity");
  ItemStack shulker=new ItemStack(Items.SHULKER_BOX);shulker.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,32))));
  check(InventoryComparator.strict(shulker,new ItemStack(Items.SHULKER_BOX))==ComparisonEngine.Status.WRONG,"nested shulker identity");
  var variants=List.of(sword,damaged,enchanted,healing,poison,shulker);
  check(ItemStack.listMatches(variants,InventoryData.decode(InventoryData.encode(variants,reg),variants.size(),reg)),"damage/enchantment/potion/shulker component round trip");
  ItemStack named=new ItemStack(Items.DIAMOND,32);named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep components"));
  List<ItemStack> items=new ArrayList<>(Collections.nCopies(27,ItemStack.EMPTY));items.set(5,named);
  var encoded=InventoryData.encode(items,reg);var decoded=InventoryData.decode(encoded,27,reg);
  check(ItemStack.listMatches(items,decoded),"registry-aware item/component round trip");
  check(!InventoryComparator.amount(named).key().equals(InventoryComparator.amount(new ItemStack(Items.DIAMOND,32)).key()),"named components differ");
  var empty=InventoryData.encode(Collections.nCopies(27,ItemStack.EMPTY),reg);check(empty.get("Items") instanceof ListTag&&empty.getListOrEmpty("Items").isEmpty(),"explicit known empty Items");
  CompoundTag broken=encoded.copy();broken.getListOrEmpty("Items").getCompoundOrEmpty(0).putString("id","missing:unavailable");
  boolean rejected=false;try{InventoryData.decode(broken,27,reg);}catch(Exception e){rejected=true;}check(rejected,"unknown item rejected");
  var snapshot=new ContainerSnapshot("minecraft:chest",Blocks.CHEST.defaultBlockState().toString(),27,System.currentTimeMillis(),net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version(),encoded);
  snapshot.blockEntityData().remove("Items");check(snapshot.blockEntityData().contains("Items"),"snapshot defensive accessor");
  Inventory inv=Minecraft.getInstance().player.getInventory();BlockPos pos=new BlockPos(15,64,15);
  for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))for(ChestType type:List.of(ChestType.LEFT,ChestType.RIGHT)){
   var state=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,facing).setValue(ChestBlock.TYPE,type);
   var menu=ChestMenu.sixRows(7,inv);var adapter=ContainerMenuAdapter.create(menu,pos,state);check(adapter!=null,"double chest adapter");
   var other=ChestBlock.getConnectedBlockPos(pos,state);var opposite=state.setValue(ChestBlock.TYPE,type==ChestType.LEFT?ChestType.RIGHT:ChestType.LEFT);
   var fromOther=ContainerMenuAdapter.create(menu,other,opposite);check(adapter.halves().equals(fromOther.halves()),"opened half independent");
   check(adapter.halves().get(0).equals(type==ChestType.RIGHT?pos:other),"right half first");check(adapter.slots().size()==54,"54 logical slots");
  }
  for(var pair:List.of(new Object[]{Blocks.CHEST,ChestMenu.threeRows(1,inv)},new Object[]{Blocks.TRAPPED_CHEST,ChestMenu.threeRows(2,inv)},new Object[]{Blocks.BARREL,ChestMenu.threeRows(3,inv)},new Object[]{Blocks.SHULKER_BOX,new ShulkerBoxMenu(4,inv)},new Object[]{Blocks.DISPENSER,new DispenserMenu(5,inv)},new Object[]{Blocks.DROPPER,new DispenserMenu(6,inv)},new Object[]{Blocks.HOPPER,new HopperMenu(7,inv)},new Object[]{Blocks.BREWING_STAND,new BrewingStandMenu(8,inv)},new Object[]{Blocks.FURNACE,new FurnaceMenu(9,inv)},new Object[]{Blocks.BLAST_FURNACE,new BlastFurnaceMenu(10,inv)},new Object[]{Blocks.SMOKER,new SmokerMenu(11,inv)})){
   var block=(Block)pair[0];var menu=(AbstractContainerMenu)pair[1];check(ContainerMenuAdapter.create(menu,pos,block.defaultBlockState())!=null,"adapter "+block);
  }
  check(ContainerMenuAdapter.create(ChestMenu.threeRows(1,inv),pos,Blocks.ENDER_CHEST.defaultBlockState())!=null&&ContainerMenuAdapter.size(Blocks.ENDER_CHEST.defaultBlockState())==0,"ender chest tracked but excluded from schematic injection");
  check(ContainerMenuAdapter.create(ChestMenu.threeRows(1,inv),pos,Blocks.FURNACE.defaultBlockState())==null,"menu mismatch rejected");
  var expectationA=new SchematicInventoryResolver.Expected(pos,Blocks.CHEST.defaultBlockState(),items,true,false,1,"a");
  var expectationB=new SchematicInventoryResolver.Expected(pos,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,ChestType.LEFT),items,true,false,1,"b");
  check(SchematicInventoryResolver.merge(expectationA,expectationB).conflict(),"overlap with incompatible chest topology is a conflict");
  log.add("Stack serialization, components, empty, invalid ids, supported menu adapters and chest halves passed");
  AreaSelection area=new AreaSelection();area.setExplicitOrigin(new BlockPos(11,60,11));
  area.addSubRegionBox(new Box(new BlockPos(17,65,18),new BlockPos(14,63,14),"negative"),false);
  area.addSubRegionBox(new Box(new BlockPos(14,63,14),new BlockPos(17,65,18),"overlap"),false);
  var schematic=LitematicaSchematic.createEmptySchematic(area,"StoragePreviewTest");
  BlockPos local=new BlockPos(1,1,1),world=new BlockPos(15,64,15);
  for(String r:area.getAllSubRegionNames()){
   schematic.getSubRegionContainer(r).set(1,1,1,Blocks.CHEST.defaultBlockState());
   CompoundData be=new CompoundData();be.putString("id","minecraft:chest");be.putInt("BurnTime",123);be.putString("LootTable","minecraft:chests/simple_dungeon");
   fi.dy.masa.malilib.util.data.tag.util.DataTypeUtils.writeBlockPosToTag(local,be);schematic.getBlockEntityMapForRegion(r).put(local,be);
  }
  var result=SessionInventoryCache.injectInto(schematic,area.getAllSubRegions(),"test","test:dim",Map.of(new CacheKey("test","test:dim",world),snapshot));
  check(result.injected()==2,"inject all overlapping regions including negative size");
  for(String r:area.getAllSubRegionNames()){
   var be=schematic.getBlockEntityMapForRegion(r).get(local);check(be.getIntOrDefault("BurnTime",0)==123,"preserve fields");check(!be.containsLenient("LootTable"),"remove loot table");
   check(ItemStack.listMatches(items,InventoryData.decode(DataConverterNbt.toVanillaCompound(be),27,reg)),"schematic Items");
  }
  var data=schematic.writeToData();data.getCompound("litematicainventories:storage").putString("TestPreserved","yes");check(data.getCompound("litematicainventories:storage").getIntOrDefault("Schema",0)==1,"root metadata");
  var loaded=new LitematicaSchematic(null,data,FileType.LITEMATICA_SCHEMATIC);check(loaded.writeToData().getCompound("litematicainventories:storage").getStringOrDefault("TestPreserved","").equals("yes"),"root metadata survives read and rewrite");
  for(String r:area.getAllSubRegionNames())check(loaded.getBlockEntityMapForRegion(r).get(local).getCompound(InventoryData.OBSERVATION).getBooleanOrDefault("Complete",false),"observation round trip");
  Path dir=Path.of("test-schematics");Files.createDirectories(dir);check(loaded.writeToFile(dir,"portable",true),"write actual litematic file");
  var reread=LitematicaSchematic.createFromFile(dir,"portable.litematic");check(reread!=null,"read actual litematic file");
  var emptySnapshot=new ContainerSnapshot(snapshot.blockId(),snapshot.state(),27,System.currentTimeMillis(),snapshot.dataVersion(),empty);
  check(SessionInventoryCache.injectInto(loaded,area.getAllSubRegions(),"test","test:dim",Map.of(new CacheKey("test","test:dim",world),emptySnapshot)).injected()==2,"empty overwrites both regions");
  for(String r:area.getAllSubRegionNames())check(DataConverterNbt.toVanillaCompound(loaded.getBlockEntityMapForRegion(r).get(local)).getListOrEmpty("Items").isEmpty(),"old items removed");
  var placement=SchematicPlacement.createTemporary(schematic,new BlockPos(-100,70,200));
  var feedback=new fi.dy.masa.litematica.gui.GuiMaterialList(new fi.dy.masa.litematica.materials.MaterialListSchematic(schematic,false));
  for(Rotation global:Rotation.values())for(Mirror gm:Mirror.values())for(Rotation sub:Rotation.values())for(Mirror sm:Mirror.values()){
   placement.setRotation(global,feedback);placement.setMirror(gm,feedback);
   placement.setSubRegionRotation("negative",sub,feedback);placement.setSubRegionMirror("negative",sm,feedback);
   for(int x=0;x<4;x++)for(int y=0;y<3;y++)for(int z=0;z<5;z++){
    var l=new BlockPos(x,y,z);var absolute=SchematicInventoryResolver.world(placement,"negative",l);
    check(SchematicInventoryResolver.local(placement,"negative",absolute).equals(l),"all transforms inverse");
    var box=placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).get("negative");
    var min=fi.dy.masa.litematica.util.PositionUtils.getMinCorner(box.getPos1(),box.getPos2());var max=fi.dy.masa.litematica.util.PositionUtils.getMaxCorner(box.getPos1(),box.getPos2());
    check(absolute.getX()>=min.getX()&&absolute.getY()>=min.getY()&&absolute.getZ()>=min.getZ()&&absolute.getX()<=max.getX()&&absolute.getY()<=max.getY()&&absolute.getZ()<=max.getZ(),"matches native placement boxes");
   }
  }
  log.add("Schematic injection/empty/loot/preservation/file round trip and 8640 placement transform cases passed");
  SessionInventoryCache.clear();SessionInventoryCache.put(new CacheKey("test-a","dim",pos),snapshot);SessionInventoryCache.put(new CacheKey("test-b","dim",pos),emptySnapshot);SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();
  var persisted=PersistentInventoryStore.load();check(persisted.size()==2,"cache persisted with separate worlds");
  Files.write(StorageSettings.DIRECTORY.resolve("containers.nbt"),new byte[]{1,2,3});check(PersistentInventoryStore.load().size()==2,"backup recovery");
  SessionInventoryCache.clear();SessionInventoryCache.flush();PersistentInventoryStore.awaitWrites();check(PersistentInventoryStore.load().isEmpty(),"clear persists");
  log.add("Cache disk round trip, world isolation, corrupted primary backup recovery, persistent clear passed");
 }
}


