package ca.techgarage.scrubians;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

public class LoadedChunkTracker {
    private static final LongSet LOADED_CHUNKS = new LongOpenHashSet();

    public static void onLoad(ServerWorld world, WorldChunk chunk) {
        LOADED_CHUNKS.add(chunk.getPos().toLong());
    }

    public static void onUnload(ServerWorld world, WorldChunk chunk) {
        LOADED_CHUNKS.remove(chunk.getPos().toLong());
    }

    public static LongSet getLoadedChunks() {
        return LOADED_CHUNKS;
    }
}
