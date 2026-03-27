package ca.techgarage.scrubians.npcs.violent;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Tracker for violent NPCs with hybrid mode support
 */
public class ViolentNpcTracker {

    private static final Map<UUID, Integer> ENTITY_TO_NPC_ID = new HashMap<>();
    private static final Map<Integer, List<UUID>> NPC_ID_TO_ENTITIES = new HashMap<>();
    private static final Map<Integer, Integer> RESPAWN_TIMERS = new HashMap<>();

    /**
     * Register entity
     */
    public static void registerEntity(Entity entity, int npcId) {
        UUID uuid = entity.getUuid();
        ENTITY_TO_NPC_ID.put(uuid, npcId);
        NPC_ID_TO_ENTITIES.computeIfAbsent(npcId, k -> new ArrayList<>()).add(uuid);
    }

    /**
     * Initialize all npcs
     */
    public static void initializeAllNpcs(ServerWorld world) {
        if (world.getPlayers().isEmpty()) {
            return;
        }

        rebuildFromWorld(world);

        for (ViolentNpcRegistry.ViolentNpcData npcData : ViolentNpcRegistry.getAllNpcs()) {
            if (npcData.spawnArea == null) continue;

            int current = getCurrentCount(world, npcData.id);
            int max = npcData.spawnArea.maxCount;

            if (current >= max) {
                continue;
            }

            int needed = max - current;

            for (int i = 0; i < needed; i++) {
                if (!spawnNpc(world, npcData.id)) {
                    break;
                }
            }
        }
    }

    /**
     * Despawn all entities for an NPC
     */
    public static void despawn(ServerWorld world, int npcId) {
        RESPAWN_TIMERS.put(npcId, 20 * 10); // respawn in 10s

        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null) {
            for (UUID uuid : new ArrayList<>(entities)) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    // If this is an AI entity in hybrid mode, also remove display entity
                    if (ViolentNpcEntity.isAiEntity(entity)) {
                        Entity displayEntity = ViolentNpcEntity.getDisplayEntity(world, entity);
                        if (displayEntity != null) {
                            displayEntity.discard();
                        }
                    }
                    // If this is a display entity, also remove AI entity
                    else if (ViolentNpcEntity.isDisplayEntity(entity)) {
                        Entity aiEntity = ViolentNpcEntity.getAiEntity(world, entity);
                        if (aiEntity != null) {
                            aiEntity.discard();
                        }
                    }

                    entity.discard();
                }
            }
        }

        ENTITY_TO_NPC_ID.entrySet().removeIf(e -> e.getValue() == npcId);
        NPC_ID_TO_ENTITIES.remove(npcId);
    }

    /**
     * Despawn all violent NPCs
     */
    public static void despawnAllViolentNpcs(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (ViolentNpcEntity.isViolentNpc(entity)) {
                // If hybrid mode, clean up both entities
                if (ViolentNpcEntity.isAiEntity(entity)) {
                    Entity displayEntity = ViolentNpcEntity.getDisplayEntity(world, entity);
                    if (displayEntity != null) {
                        displayEntity.discard();
                    }
                } else if (ViolentNpcEntity.isDisplayEntity(entity)) {
                    Entity aiEntity = ViolentNpcEntity.getAiEntity(world, entity);
                    if (aiEntity != null) {
                        aiEntity.discard();
                    }
                }

                entity.discard();
            }
        }

        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();
        RESPAWN_TIMERS.clear();
        ViolentNpcEntity.clearHybridTracking();
    }

    /**
     * Unregister entity
     */
    public static void unregisterEntity(UUID entityUuid, int npcId) {
        ENTITY_TO_NPC_ID.remove(entityUuid);
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null) {
            entities.remove(entityUuid);
        }
    }

    /**
     * Notify NPC death
     */
    public static void notifyNpcDeath(ServerWorld world, int npcId, UUID entityUuid) {
        // Get the entity to check if it's hybrid mode
        Entity deadEntity = world.getEntity(entityUuid);

        // If this is a display entity death, we need to kill the AI entity too
        if (deadEntity != null && ViolentNpcEntity.isDisplayEntity(deadEntity)) {
            Entity aiEntity = ViolentNpcEntity.getAiEntity(world, deadEntity);
            if (aiEntity != null && aiEntity.isAlive()) {
                aiEntity.kill(world);
            }
        }
        // If this is an AI entity death, kill the display entity
        else if (deadEntity != null && ViolentNpcEntity.isAiEntity(deadEntity)) {
            Entity displayEntity = ViolentNpcEntity.getDisplayEntity(world, deadEntity);
            if (displayEntity != null && displayEntity.isAlive()) {
                displayEntity.discard(); // Use discard instead of kill to avoid triggering death event
            }
        }

        unregisterEntity(entityUuid, npcId);

        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt =
                ViolentNpcRegistry.getNpcById(Optional.of(npcId));

        if (npcDataOpt.isEmpty()) return;
        var npcData = npcDataOpt.get();

        if (npcData.spawnArea == null || !npcData.persistent) return;

        int currentCount = getCurrentCount(world, npcId);
        int needed = npcData.spawnArea.maxCount - currentCount;

        if (needed > 0 && !RESPAWN_TIMERS.containsKey(npcId)) {
            RESPAWN_TIMERS.put(npcId, npcData.spawnArea.respawnDelayTicks);
        }
    }

    /**
     * Main tick function
     */
    public static void tick(ServerWorld world) {
        if (world.getPlayers().isEmpty()) {
            return;
        }

        // Tick hybrid NPCs (sync display entities with AI entities)
        ViolentNpcEntity.tickHybridNpcs(world);

        // Tick respawn timers
        Iterator<Map.Entry<Integer, Integer>> it = RESPAWN_TIMERS.entrySet().iterator();
        List<Integer> toSpawn = new ArrayList<>();

        while (it.hasNext()) {
            var entry = it.next();
            int timer = entry.getValue() - 1;

            if (timer <= 0) {
                toSpawn.add(entry.getKey());
                it.remove();
            } else {
                entry.setValue(timer);
            }
        }

        for (int npcId : toSpawn) {
            spawnNpc(world, npcId);
        }

        // Periodic spawn verification
        if (world.getTimeOfDay() % 100 == 0) {
            for (var npcData : ViolentNpcRegistry.getAllNpcs()) {
                if (npcData.spawnArea == null) continue;
                if (RESPAWN_TIMERS.containsKey(npcData.id)) continue;

                int current = getCurrentCount(world, npcData.id);
                int needed = npcData.spawnArea.maxCount - current;

                for (int i = 0; i < needed; i++) {
                    if (!spawnNpc(world, npcData.id)) break;
                }
            }
        }

        // Cleanup
        if (world.getTimeOfDay() % 40 == 0) {
            cleanupDeadEntities(world);
        }
    }

    /**
     * Get current count - only counts AI entities (or standard entities)
     * Display entities in hybrid mode are not counted separately
     */
    private static int getCurrentCount(ServerWorld world, int npcId) {
        int count = 0;

        for (Entity entity : world.iterateEntities()) {
            if (!entity.isAlive()) continue;
            if (!ViolentNpcEntity.isViolentNpc(entity)) continue;

            // Skip display entities - only count AI entities or standard entities
            if (ViolentNpcEntity.isDisplayEntity(entity)) continue;

            Optional<Integer> entityNpcId = ViolentNpcEntity.getNpcId(entity);
            if (entityNpcId.isPresent() && entityNpcId.get() == npcId) {
                count++;
            }
        }

        return count;
    }

    /**
     * Spawn an NPC
     */
    public static boolean spawnNpc(ServerWorld world, int npcId) {
        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt =
                ViolentNpcRegistry.getNpcById(Optional.of(npcId));

        if (npcDataOpt.isEmpty()) return false;
        var npcData = npcDataOpt.get();

        if (npcData.spawnArea == null) return false;

        int current = getCurrentCount(world, npcId);
        if (current >= npcData.spawnArea.maxCount) return false;

        Vec3d pos = npcData.spawnArea.getRandomPosition();
        Entity entity = ViolentNpcEntity.spawnViolentNpc(
                world, npcId, npcData.entityType, pos
        );

        if (entity != null) {
            registerEntity(entity, npcId);

            // If hybrid mode, also register the display entity
            if (ViolentNpcEntity.isAiEntity(entity)) {
                Entity displayEntity = ViolentNpcEntity.getDisplayEntity(world, entity);
                if (displayEntity != null) {
                    registerEntity(displayEntity, npcId);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Cleanup dead entities
     */
    private static void cleanupDeadEntities(ServerWorld world) {
        List<UUID> toRemove = new ArrayList<>();

        for (UUID uuid : ENTITY_TO_NPC_ID.keySet()) {
            Entity entity = world.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                toRemove.add(uuid);
            }
        }

        for (UUID uuid : toRemove) {
            Integer npcId = ENTITY_TO_NPC_ID.remove(uuid);
            if (npcId != null) {
                List<UUID> list = NPC_ID_TO_ENTITIES.get(npcId);
                if (list != null) list.remove(uuid);
            }
        }
    }

    /**
     * Rebuild from world
     */
    public static void rebuildFromWorld(ServerWorld world) {
        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();

        for (Entity entity : world.iterateEntities()) {
            if (ViolentNpcEntity.isViolentNpc(entity)) {
                ViolentNpcEntity.getNpcId(entity)
                        .ifPresent(id -> registerEntity(entity, id));
            }
        }
    }

    /**
     * Clear all data
     */
    public static void clear() {
        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();
        RESPAWN_TIMERS.clear();
        ViolentNpcEntity.clearHybridTracking();
    }
}