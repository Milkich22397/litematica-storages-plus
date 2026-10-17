package dev.asaul.litematicainventories.mixin;
import dev.asaul.litematicainventories.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
 @Inject(method="setServerVerifiedBlockState",at=@At("RETURN"))
 private void storage$invalidate(BlockPos pos,BlockState state,int flags,CallbackInfo ci){
  var c=Minecraft.getInstance();if(c.level!=(Object)this)return;
  var key=new CacheKey(SessionContext.serverId(c),SessionContext.dimensionId(c),pos);SessionInventoryCache.get(key);TrackedStorageStore.blockChanged(pos);
 }
}