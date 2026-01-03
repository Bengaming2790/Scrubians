package ca.techgarage.scrubians.npcs;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Tracks and manages violent NPC spawning and respawning
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

        System.out.println("[Scrubians] Registered violent NPC entity: " + entity.getType().getName().getString() + " for NPC #" + npcId);
    }

    /**
     * Called when an entity dies - starts respawn timer if needed
     */
    public static void onEntityDeath(Entity entity) {
        UUID uuid = entity.getUuid();
        Integer npcId = ENTITY_TO_NPC_ID.get(uuid);

        if (npcId != null) {
            System.out.println("[Scrubians] Violent NPC entity died (NPC #" + npcId + ")");

            // Remove from tracking
            ENTITY_TO_NPC_ID.remove(uuid);
            List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
            if (entities != null) {
                entities.remove(uuid);
            }

            // Check if should respawn
            ViolentNpcRegistry.getNpcById(npcId).ifPresent(npcData -> {
                if (npcData.persistent && npcData.spawnArea != null) {
                    RESPAWN_TIMERS.put(npcId, npcData.spawnArea.respawnDelayTicks);
                    System.out.println("[Scrubians] Will respawn NPC #" + npcId + " in " + npcData.spawnArea.respawnDelayTicks + " ticks");
                }
            });
        }
    }

    /**
     * Tick respawn timers and spawn entities
     */
    public static void tick(ServerWorld world) {
        // Tick down respawn timers
        Iterator<Map.Entry<Integer, Integer>> it = RESPAWN_TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int npcId = entry.getKey();
            int timer = entry.getValue() - 1;

            if (timer <= 0) {
                // Time to respawn
                it.remove();
                spawnNpc(world, npcId);
            } else {
                entry.setValue(timer);
            }
        }

        // Ensure NPCs are spawned up to their max count
        for (ViolentNpcRegistry.ViolentNpcData npcData : ViolentNpcRegistry.getAllNpcs()) {
            int currentCount = getCurrentCount(npcData.id);
            int needed = npcData.spawnArea.maxCount - currentCount;

            if (needed > 0 && !RESPAWN_TIMERS.containsKey(npcData.id)) {
                for (int i = 0; i < needed; i++) {
                    spawnNpc(world, npcData.id);
                }
            }
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
     * Spawn a violent NPC
     */
    public static void spawnNpc(ServerWorld world, int npcId) {
        ViolentNpcRegistry.getNpcById(npcId).ifPresent(npcData -> {
            // Check if already at max count
            if (getCurrentCount(npcId) >= npcData.spawnArea.maxCount) {
                return;
            }

            Vec3d spawnPos = npcData.spawnArea.getRandomPosition();
            Entity entity = npcData.getEntityType().create(world, SpawnReason.PATROL);

            if (entity == null) {
                System.err.println("[Scrubians] Failed to create entity of type: " + npcData.entityType);
                return;
            }

            // Set position
            entity.setPosition(spawnPos);

            // Set custom name
            if (npcData.name != null && !npcData.name.isEmpty()) {
                entity.setCustomName(Text.literal(npcData.name));
                entity.setCustomNameVisible(true);
            }

            // Apply stats if it's a living entity
            if (entity instanceof LivingEntity living) {
                applyStats(living, npcData.stats);
            }

            // Make glowing if enabled
            if (npcData.stats.glowing) {
                entity.setGlowing(true);
            }

            // Spawn entity
            if (world.spawnEntity(entity)) {
                registerEntity(entity, npcId);
                System.out.println("[Scrubians] Spawned violent NPC #" + npcId + " (" + npcData.name + ") at " + spawnPos);
            } else {
                System.err.println("[Scrubians] Failed to spawn violent NPC #" + npcId);
            }
        });
    }

    /**
     * Apply custom stats to a living entity
     */
    private static void applyStats(LivingEntity entity, ViolentNpcRegistry.Stats stats) {
        // Max health
        if (entity.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            entity.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(stats.health);
            entity.setHealth((float) stats.health);
        }

        // Attack damage
        if (entity.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            entity.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(stats.attackDamage);
        }

        // Movement speed
        if (entity.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
            double baseSpeed = entity.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getBaseValue();
            entity.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * stats.speed);
        }

        // Knockback resistance
        if (entity.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE) != null) {
            entity.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(stats.knockbackResistance);
        }

        // Follow range
        if (entity.getAttributeInstance(EntityAttributes.FOLLOW_RANGE) != null) {
            entity.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).setBaseValue(stats.followRange);
        }
    }

    /**
     * Force despawn all entities for an NPC
     */
    public static void despawnNpc(ServerWorld world, int npcId) {
        List<UUID> entities = NPC_ID_TO_ENTITIES.get(npcId);
        if (entities != null) {
            for (UUID uuid : new ArrayList<>(entities)) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
                ENTITY_TO_NPC_ID.remove(uuid);
            }
            entities.clear();
        }
        RESPAWN_TIMERS.remove(npcId);
    }

    /**
     * Check if an entity is a violent NPC
     */
    public static boolean isViolentNpc(Entity entity) {
        return ENTITY_TO_NPC_ID.containsKey(entity.getUuid());
    }

    /**
     * Get the NPC ID for an entity
     */
    public static Optional<Integer> getNpcId(Entity entity) {
        return Optional.ofNullable(ENTITY_TO_NPC_ID.get(entity.getUuid()));
    }

    /**
     * Clear all tracking data (for server shutdown)
     */
    public static void clear() {
        ENTITY_TO_NPC_ID.clear();
        NPC_ID_TO_ENTITIES.clear();
        RESPAWN_TIMERS.clear();
    }
}