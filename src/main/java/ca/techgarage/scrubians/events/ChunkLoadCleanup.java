package ca.techgarage.scrubians.events;

import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;
import ca.techgarage.scrubians.Scrubians; 
import java.util.ArrayList;
import java.util.List;

/**
 * Cleans up invalid mannequins as chunks load
 */
public class ChunkLoadCleanup {

    private static int totalCleaned = 0;

    /**
     * Called when a chunk loads - removes invalid mannequins in that chunk
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        // Get bounding box for the chunk (chunks are 16x16, full world height)
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();

        // Create box covering entire chunk (all Y levels)
        Box chunkBox = new Box(
                chunkX, world.getBottomY(), chunkZ,
                chunkX + 16, 2048, chunkZ + 16
        );

        // Get all mannequins in this chunk
        List<MannequinEntity> toRemove = new ArrayList<>();

        world.getEntitiesByClass(MannequinEntity.class, chunkBox, entity -> true).forEach(mannequin -> {
            // Check if it's our tracking mannequin
            if (mannequin instanceof TrackingMannequinEntity trackingNpc) {
                int npcId = trackingNpc.getNpcId();

                // Check if ID is invalid or not in registry
                if (npcId < 0 || NpcRegistry.getNpcById(npcId).isEmpty()) {
                    toRemove.add(mannequin);
                }
            }
            // Leave regular mannequins alone - they're not ours
        });

        // Remove invalid mannequins
        if (!toRemove.isEmpty()) {
            for (MannequinEntity mannequin : toRemove) {
                String name = mannequin.getCustomName() != null ? mannequin.getCustomName().getString() : "Unknown";
                int id = mannequin instanceof TrackingMannequinEntity ? ((TrackingMannequinEntity) mannequin).getNpcId() : -1;

                Scrubians.logger("info", "[Scrubians] Chunk cleanup: Removing invalid mannequin '" + name + "' (ID: " + id + ") at " + mannequin.getEntityPos());
                mannequin.discard();
                totalCleaned++;
            }
        }
    }

    /**
     * Get total count of cleaned mannequins this session
     */
    public static int getTotalCleaned() {
        return totalCleaned;
    }

    /**
     * Reset cleanup counter (for new server session)
     */
    public static void reset() {
        totalCleaned = 0;
    }
}
