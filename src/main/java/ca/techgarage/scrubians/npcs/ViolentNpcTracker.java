package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.Scrubians;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Tracks and manages violent NPC spawning and respawning
 * Works with vanilla entities marked by ViolentNpcEntity utility class
 */
public class ViolentNpcTracker {

    // Map of entity UUID to violent NPC ID
    private static final Map<UUID, Integer> ENTITY_TO_NPC_ID = new HashMap<>();

    // Map of NPC ID to list of entity UUIDs
    private static final Map<Integer, List<UUID>> NPC_ID_TO_ENTITIES = new HashMap<>();

    // Map of NPC ID to respawn timer (in ticks)
    private static final Map<Integer, Integer> RESPAWN_TIMERS = new HashMap<>();

    /**
     * Register an entity as belonging to a violent NPC
     */
    public static void registerEntity(Entity entity, int npcId) {
        UUID uuid = entity.getUuid();
        ENTITY_TO_NPC_ID.put(uuid, npcId);

        NPC_ID_TO_ENTITIES.computeIfAbsent(npcId, k -> new ArrayList<>()).add(uuid);

        Scrubians.logger("[Scrubians] Registered violent NPC entity for NPC #" + npcId);
    }

    /**
     * Called when a violent NPC entity dies
     * This should be called when the entity's death is detected
     */
    public static void notifyNpcDeath(ServerWorld world, Optional<Integer> npcId, UUID entityUuid) {
        Scrubians.logger("[Scrubians] Violent NPC entity died (NPC #" + npcId + ")");

        // Remove from tracking
        ENTITY_TO_NPC_ID.remove(entityUuid);
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null) {
            entities.remove(entityUuid);
        }

        // Check if should respawn
        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt = ViolentNpcRegistry.getNpcById(npcId);
        if (npcDataOpt.isPresent()) {
            ViolentNpcRegistry.ViolentNpcData npcData = npcDataOpt.get();
            if (npcData.spawnArea != null) {
                // Only set respawn timer if not already set and not at max count
                if (!RESPAWN_TIMERS.containsKey(npcId)) {
                    int realNpcId = npcId.orElse(-1);
                    int currentCount = getCurrentCount(realNpcId);
                    if (currentCount < npcData.spawnArea.maxCount) {
                        RESPAWN_TIMERS.put(realNpcId, npcData.spawnArea.respawnDelayTicks);
                        Scrubians.logger("[Scrubians] Will respawn NPC #" + npcId +
                                " in " + (npcData.spawnArea.respawnDelayTicks / 20) + " seconds");
                    }
                }
            }
        }
    }

    /**
     * Tick respawn timers and spawn entities
     */
    public static void tick(ServerWorld world) {
        // Tick down respawn timers
        // Use a list to collect NPCs to spawn to avoid ConcurrentModificationException
        List<Integer> toSpawn = new ArrayList<>();

        Iterator<Map.Entry<Integer, Integer>> it = RESPAWN_TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int npcId = entry.getKey();
            int timer = entry.getValue() - 1;

            if (timer <= 0) {
                // Time to respawn
                it.remove();
                toSpawn.add(npcId);
            } else {
                entry.setValue(timer);
            }
        }

        // Spawn NPCs after iteration is complete
        for (int npcId : toSpawn) {
            spawnNpc(world, npcId);
        }

        // Ensure NPCs are spawned up to their max count
        // Check every 5 seconds to avoid excessive spawning attempts
        if (world.getTime() % 100 == 0) {
            for (ViolentNpcRegistry.ViolentNpcData npcData : ViolentNpcRegistry.getAllNpcs()) {
                if (npcData.spawnArea == null) continue;

                int currentCount = getCurrentCount(npcData.id);
                int needed = npcData.spawnArea.maxCount - currentCount;

                if (needed > 0 && !RESPAWN_TIMERS.containsKey(npcData.id)) {
                    for (int i = 0; i < needed; i++) {
                        spawnNpc(world, npcData.id);
                    }
                }
            }
        }

        // Clean up dead entities from tracking (in case death wasn't notified)
        if (world.getTime() % 200 == 0) {
            cleanupDeadEntities(world);
        }
    }

    /**
     * Get current count of alive entities for an NPC
     */
    private static int getCurrentCount(int npcId) {
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        return entities != null ? entities.size() : 0;
    }

    /**
     * Spawn a violent NPC entity
     */
    public static void spawnNpc(ServerWorld world, int npcId) {
        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt = ViolentNpcRegistry.getNpcById(Optional.of(npcId));
        if (!npcDataOpt.isPresent()) {
            Scrubians.logger("[Scrubians] NPC #" + npcId + " not found in registry");
            return;
        }

        ViolentNpcRegistry.ViolentNpcData npcData = npcDataOpt.get();

        if (npcData.spawnArea == null) {
            Scrubians.logger("[Scrubians] NPC #" + npcId + " has no spawn area");
            return;
        }

        // Check if already at max count
        if (getCurrentCount(npcId) >= npcData.spawnArea.maxCount) {
            return;
        }

        Vec3d spawnPos = npcData.spawnArea.getRandomPosition();

        // Use ViolentNpcEntity utility class to spawn the entity
        Entity entity = ViolentNpcEntity.spawnViolentNpc(world, npcId, npcData.entityType, spawnPos);

        if (entity != null) {
            registerEntity(entity, npcId);
        } else {
            Scrubians.logger("[Scrubians] Failed to spawn violent NPC #" + npcId);
        }
    }

    /**
     * Force despawn all entities for an NPC
     */
    public static void despawnNpc(ServerWorld world, int npcId) {
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null && !entities.isEmpty()) {
            int count = entities.size();
            for (UUID uuid : new ArrayList<>(entities)) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
                ENTITY_TO_NPC_ID.remove(uuid);
            }
            entities.clear();
            Scrubians.logger("[Scrubians] Despawned " + count + " entities for NPC #" + npcId);
        }
        RESPAWN_TIMERS.remove(npcId);
    }

    /**
     * Check if an entity is a violent NPC
     */
    public static boolean isViolentNpc(Entity entity) {
        return ViolentNpcEntity.isViolentNpc(entity) || ENTITY_TO_NPC_ID.containsKey(entity.getUuid());
    }

    /**
     * Get the NPC ID for an entity
     */
    public static Optional<Integer> getNpcId(Entity entity) {
        // First try the tracker
        Integer npcId = ENTITY_TO_NPC_ID.get(entity.getUuid());
        if (npcId != null) {
            return Optional.of(npcId);
        }

        // Fall back to reading from entity NBT
        return ViolentNpcEntity.getNpcId(entity);
    }

    /**
     * Clean up entities that no longer exist in the world
     */
    private static void cleanupDeadEntities(ServerWorld world) {
        Iterator<Map.Entry<UUID, Integer>> it = ENTITY_TO_NPC_ID.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID uuid = entry.getKey();
            Entity entity = world.getEntity(uuid);

            if (entity == null || !entity.isAlive()) {
                int npcId = entry.getValue();
                it.remove();

                List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
                if (entities != null) {
                    entities.remove(uuid);
                }
            }
        }
    }

    /**
     * Get all entities for a specific NPC
     */
    public static List<Entity> getEntitiesForNpc(ServerWorld world, int npcId) {
        List<Entity> result = new ArrayList<>();
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);

        if (entities != null) {
            for (UUID uuid : entities) {
                Entity entity = world.getEntity(uuid);
                if (entity != null && entity.isAlive()) {
                    result.add(entity);
                }
            }
        }

        return result;
    }

    /**
     * Force update stats for all entities of an NPC
     * Useful after changing stats in the registry
     */
    public static void updateStatsForNpc(ServerWorld world, int npcId) {
        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt = ViolentNpcRegistry.getNpcById(Optional.of(npcId));
        if (!npcDataOpt.isPresent()) return;

        ViolentNpcRegistry.ViolentNpcData npcData = npcDataOpt.get();
        List<Entity> entities = getEntitiesForNpc(world, npcId);

        for (Entity entity : entities) {
            ViolentNpcEntity.updateEntityStats(entity, npcData.stats);
        }

        Scrubians.logger("[Scrubians] Updated stats for " + entities.size() +
                " entities of NPC #" + npcId);
    }

    /**
     * Get statistics about all tracked NPCs
     */
    public static Map<Integer, Integer> getSpawnCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Map.Entry<Integer, List<UUID>> entry : NPC_ID_TO_ENTITIES.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    /**
     * Clear all tracking data (for server shutdown)
     */
    public static void clear() {
        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();
        RESPAWN_TIMERS.clear();
        Scrubians.logger("[Scrubians] ViolentNpcTracker cleared");
    }
}