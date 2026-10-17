package dev.asaul.litematicainventories;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** ClientLevel.hasChunk() always returns true; query the actual cache without a fallback chunk. */
public final class ClientChunks {
    public static boolean loaded(ClientLevel level,BlockPos pos){
        return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;
    }
    private ClientChunks(){}
}
