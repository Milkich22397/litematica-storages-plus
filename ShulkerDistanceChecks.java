package dev.asaul.litematicainventories;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real server menu and player teleport; no fake empty-chunk fixtures. */
public final class ShulkerDistanceChecks {
    private static int stage,ticks;
    private static BlockPos pos;
    private static Vec3 home;
    private static CacheKey cacheKey;
    private static TrackedStorageStore.Key ledgerKey;
    private static TrackedStorageStore.Entry saved;
    private static long total;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void teleport(Minecraft c,boolean away){
        c.getSingleplayerServer().execute(()->{
            var p=c.getSingleplayerServer().getPlayerList().getPlayer(c.player.getUUID());
            p.setGameMode(away?GameType.SPECTATOR:GameType.SURVIVAL);
            p.teleportTo(home.x+(away?1024:0),home.y,home.z);
        });
    }
    private static void open(Minecraft c){c.gameMode.useItemOn(c.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));}
    private static TrackedStorageStore.Entry entry(){return TrackedStorageStore.snapshot().stream().filter(e->e.key().equals(ledgerKey)).findFirst().orElseThrow();}
    public static void start(Minecraft c){
        stage=0;ticks=0;home=c.player.position();pos=c.player.blockPosition().offset(0,0,2);cacheKey=new CacheKey(SessionContext.serverId(c),SessionContext.dimensionId(c),pos);
        StorageSettings.INSTANCE.recording=true;ContainerRecorder.recordingChanged();
        c.getSingleplayerServer().execute(()->{
            var level=c.getSingleplayerServer().overworld();level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,Blocks.SHULKER_BOX.defaultBlockState(),3);
            var be=(ShulkerBoxBlockEntity)level.getBlockEntity(pos);be.setItem(0,new ItemStack(Items.STONE,64));be.setChanged();
        });
    }
    private static void next(int n){stage=n;ticks=0;}
    public static boolean tick(Minecraft c,List<String> log){
        if(++ticks>1000)throw new AssertionError("Shulker distance timeout "+stage);
        switch(stage){
            case 0 -> {if(ClientChunks.loaded(c.level,pos)&&c.level.getBlockState(pos).is(Blocks.SHULKER_BOX)&&ticks>10){open(c);next(1);}}
            case 1 -> {if(ticks%40==0&&c.gui.screen()==null)open(c);var o=ContainerRecorder.active();if(o!=null&&o.synchronizedMenu&&o.adapter.copyItems().getFirst().getCount()==64){
                ledgerKey=TrackedStorageStore.key(o);TrackedStorageStore.toggle(o);saved=entry();
                check(SessionInventoryCache.get(cacheKey)!=null,"shulker schematic cache captured");
                ResourceAccounting.tick(c);total=ResourceAccounting.available(new ItemStack(Items.STONE));
                c.player.closeContainer();teleport(c,true);next(2);
            }}
            case 2 -> {if(c.player.position().distanceTo(home)>900&&!ClientChunks.loaded(c.level,pos)){
                check(c.level.hasChunkAt(pos),"regression: ClientLevel.hasChunkAt misleadingly reports true outside loaded chunks");
                check(c.level.getBlockState(pos).isAir(),"unloaded shulker resolves to fallback air");
                TrackedStorageStore.validateLoaded(c);TrackedStorageStore.blockChanged(pos);ResourceAccounting.tick(c);
                check(entry().valid()&&entry().data().equals(saved.data()),"unloaded shulker keeps valid exact contents");
                check(ResourceAccounting.available(new ItemStack(Items.STONE))==total,"resource total remains unchanged at distance");
                check(SessionInventoryCache.get(cacheKey)!=null,"schematic snapshot survives unloaded fallback air");
                TrackedStorageStore.awaitWrites();
                var disk=TrackedStorageStore.decode(readLedger());check(disk.get(ledgerKey).valid()&&disk.get(ledgerKey).data().equals(saved.data()),"unloaded contents persist on disk");
                next(3);
            }}
            case 3 -> {if(ticks>45){TrackedStorageStore.validateLoaded(c);ResourceAccounting.tick(c);check(entry().valid()&&ResourceAccounting.available(new ItemStack(Items.STONE))==total,"repeated periodic checks while away preserve resources");teleport(c,false);next(4);}}
            case 4 -> {if(c.player.position().distanceTo(home)<3&&ClientChunks.loaded(c.level,pos)&&c.level.getBlockState(pos).is(Blocks.SHULKER_BOX)&&ticks>10){TrackedStorageStore.validateLoaded(c);check(entry().valid(),"returning to same shulker remains valid");open(c);next(5);}}
            case 5 -> {if(ticks%40==0&&c.gui.screen()==null)open(c);var o=ContainerRecorder.active();if(o!=null&&o.synchronizedMenu){check(TrackedStorageStore.tracked(o),"returned shulker stays marked");
                c.getSingleplayerServer().execute(()->{var be=(ShulkerBoxBlockEntity)c.getSingleplayerServer().overworld().getBlockEntity(pos);be.setItem(0,new ItemStack(Items.STONE,63));be.setChanged();});next(6);
            }}
            case 6 -> {var o=ContainerRecorder.active();if(o!=null&&o.adapter.copyItems().getFirst().getCount()==63){ResourceAccounting.tick(c);if(ResourceAccounting.available(new ItemStack(Items.STONE))!=total-1)return false;
                c.player.closeContainer();c.getSingleplayerServer().execute(()->c.getSingleplayerServer().overworld().setBlock(pos,Blocks.AIR.defaultBlockState(),3));next(7);
            }}
            case 7 -> {if(c.level.getBlockState(pos).isAir()){TrackedStorageStore.validateLoaded(c);ResourceAccounting.tick(c);check(!entry().valid()&&ResourceAccounting.available(new ItemStack(Items.STONE))==total-64,"actually removed loaded shulker still excluded");
                log.add("DISTANCE LIVE PASS: tracked shulker64, teleport1024, real chunk unload/fallback air, periodic checks and on-disk snapshot retained; return/reopen63 updates count; actual loaded removal excludes storage");return true;
            }}
        }
        return false;
    }
    private static net.minecraft.nbt.CompoundTag readLedger(){try{return net.minecraft.nbt.NbtIo.readCompressed(StorageSettings.DIRECTORY.resolve("tracked-storages.nbt"),net.minecraft.nbt.NbtAccounter.create(16*1024*1024));}catch(java.io.IOException e){throw new RuntimeException(e);}}
}
