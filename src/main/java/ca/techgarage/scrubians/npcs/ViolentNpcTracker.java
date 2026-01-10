package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.Scrubians;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * The type Violent npc tracker.
 */
public class ViolentNpcTracker {

    private static final Map<UUID, Integer> ENTITY_TO_NPC_ID = new HashMap<>();
    private static final Map<Integer, List<UUID>> NPC_ID_TO_ENTITIES = new HashMap<>();
    private static final Map<Integer, Integer> RESPAWN_TIMERS = new HashMap<>();

    /**
     * Register entity.
     *
     * @param entity the entity
     * @param npcId  the npc id
     */
    public static void registerEntity(Entity entity, int npcId) {
        UUID uuid = entity.getUuid();
        ENTITY_TO_NPC_ID.put(uuid, npcId);
        NPC_ID_TO_ENTITIES.computeIfAbsent(npcId, k -> new ArrayList<>()).add(uuid);

    }

    /**
     * Initialize all npcs.
     *
     * @param world the world
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
     * Despawn.
     *
     * @param world the world
     * @param npcId the npc id
     */
    public static void despawn(ServerWorld world, int npcId) {
        RESPAWN_TIMERS.put(npcId, 20 * 10); // respawn in 10s

        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null) {
            for (UUID uuid : new ArrayList<>(entities)) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
        }

        ENTITY_TO_NPC_ID.entrySet().removeIf(e -> e.getValue() == npcId);
        NPC_ID_TO_ENTITIES.remove(npcId);
    }

    public static void despawnAllViolentNpcs(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (ViolentNpcEntity.isViolentNpc(entity)) {
                entity.discard();
            }
        }

        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();
        RESPAWN_TIMERS.clear();
    }


    /**
     * Unregister entity.
     *
     * @param entityUuid the entity uuid
     * @param npcId      the npc id
     */
    public static void unregisterEntity(UUID entityUuid, int npcId) {
        ENTITY_TO_NPC_ID.remove(entityUuid);
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null) {
            entities.remove(entityUuid);
        }
    }

    /**
     * Notify npc death.
     *
     * @param world      the world
     * @param npcId      the npc id
     * @param entityUuid the entity uuid
     */
    public static void notifyNpcDeath(ServerWorld world, int npcId, UUID entityUuid) {
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
     * Tick.
     *
     * @param world the world
     */
    public static void tick(ServerWorld world) {

        if (world.getPlayers().isEmpty()) {
            return;
        }
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
        if (world.getTime() % 100 == 0) {
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
        if (world.getTime() % 40 == 0) {
            cleanupDeadEntities(world);
        }
    }

    private static int getCurrentCount(ServerWorld world, int npcId) {
        int count = 0;

        for (Entity entity : world.iterateEntities()) {
            if (!entity.isAlive()) continue;
            if (!ViolentNpcEntity.isViolentNpc(entity)) continue;

            Optional<Integer> id = ViolentNpcEntity.getNpcId(entity);
            if (id.isPresent() && id.get() == npcId) {
                count++;
            }
        }

        return count;
    }

    private static int getCurrentCountCached(int npcId) {
        List<UUID> list = NPC_ID_TO_ENTITIES.get(npcId);
        return list == null ? 0 : list.size();
    }


    /**
     * Spawns npc.
     *
     * @param world the world
     * @param npcId the npc id
     * @return the boolean
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
            return true;
        }

        return false;
    }


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
     * Rebuild from world.
     *
     * @param world the world
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
     * Clear.
     */
    public static void clear() {
        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();
        RESPAWN_TIMERS.clear();
    }
}
