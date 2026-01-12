package ca.techgarage.scrubians.events;

import ca.techgarage.scrubians.Scrubians;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcEntity;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcRegistry;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcTracker;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cleans up invalid violent NPCs as chunks load
 */
public class ViolentNpcChunkCleanup {

    private static int totalCleaned = 0;

    /**
     * Called when a chunk loads - removes invalid violent NPCs in that chunk
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        // Get bounding box for the chunk
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();

        Box chunkBox = new Box(
                chunkX, world.getBottomY(), chunkZ,
                chunkX + 16, 2048, chunkZ + 16
        );

        // Get all entities in this chunk
        List<Entity> toRemove = new ArrayList<>();

        world.getEntitiesByClass(Entity.class, chunkBox, entity -> true).forEach(entity -> {
            // Check if it's a violent NPC
            if (ViolentNpcEntity.isViolentNpc(entity)) {
                int npcId = ViolentNpcEntity.getNpcId(entity).orElse(-1);

                // Check if ID is invalid or not in registry
                if (npcId < 0 || ViolentNpcRegistry.getNpcById(Optional.of(npcId)).isEmpty()) {
                    toRemove.add(entity);
                }
            }
        });

        // Remove invalid violent NPCs and notify tracker
        if (!toRemove.isEmpty()) {
            for (Entity entity : toRemove) {
                String name = entity.getCustomName() != null ? entity.getCustomName().getString() : "Unknown";
                int id = ViolentNpcEntity.getNpcId(entity).orElse(-1);

                Scrubians.logger("info", "[Scrubians] Chunk cleanup: Removing invalid violent NPC '" +
                        name + "' (ID: " + id + ") at " + entity.getEntityPos());

                // Notify tracker BEFORE discarding
//                if (id >= 0) {
//                    ViolentNpcTracker.unregisterEntity(entity.getUuid(), id);
//                }

                entity.discard();
                totalCleaned++;
            }
        }
    }

    /**
     * Clean up ALL violent NPCs in the entire world
     * Used on server start to ensure clean state
     */
    public static void cleanupAllViolentNpcs(ServerWorld world) {
        List<Entity> toRemove = new ArrayList<>();
        int count = 0;

        // Iterate through all entities in the world
        for (Entity entity : world.iterateEntities()) {
            if (ViolentNpcEntity.isViolentNpc(entity)) {
                toRemove.add(entity);
                count++;
            }
        }

        // Remove all violent NPCs and notify tracker
        for (Entity entity : toRemove) {
            String name = entity.getCustomName() != null ? entity.getCustomName().getString() : "Unknown";
            int id = ViolentNpcEntity.getNpcId(entity).orElse(-1);

            Scrubians.logger("info", "[Scrubians] Server start cleanup: Removing violent NPC '" +
                    name + "' (ID: " + id + ")");
            entity.discard();
        }

        if (count > 0) {
            Scrubians.logger("info", "[Scrubians] Cleaned up " + count + " violent NPCs from world on server start");
        }

        // Clear the tracker to reset all tracking data
        ViolentNpcTracker.clear();
    }

    /**
     * Get total count of cleaned violent NPCs this session
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