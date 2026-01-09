package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.Scrubians;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Server-side only violent NPC spawner
 * Uses vanilla entity types so no client-side mod is required
 * Marks entities with NBT data to track them
 */
public class ViolentNpcEntity {

    private static final String NPC_ID_TAG = "ScrubianViolentNpcId";
    private static final String BASE_TYPE_TAG = "ScrubianBaseType";

    /**
     * Spawn a violent NPC using a vanilla entity type
     *
     * @param world            The server world
     * @param npcId            The NPC ID from registry
     * @param entityTypeString The vanilla entity type (e.g., "zombie", "skeleton")
     * @param spawnPos         Position to spawn at
     * @return The spawned entity, or null if failed
     */
    public static Entity spawnViolentNpc(ServerWorld world, int npcId, String entityTypeString, Vec3d spawnPos) {
        // Get the entity type from vanilla registry
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.ofVanilla(entityTypeString));

        if (entityType == null) {
            Scrubians.logger("info", "[Scrubians] Unknown entity type: " + entityTypeString);
            return null;
        }

        // Create the entity
        Entity entity = entityType.create(world, SpawnReason.COMMAND);

        if (entity == null) {
            Scrubians.logger("info", "[Scrubians] Failed to create entity of type: " + entityTypeString);
            return null;
        }

        // Set position
        entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);

        // Mark entity with NPC ID using custom data component
        NbtCompound customNbt = new NbtCompound();
        customNbt.putInt(NPC_ID_TAG, npcId);
        customNbt.putString(BASE_TYPE_TAG, entityTypeString);
        entity.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customNbt));

        // Get NPC data and apply customizations
        ViolentNpcRegistry.getNpcById(Optional.of(npcId)).ifPresent(npcData -> {
            // Set custom name
            if (npcData.name != null && !npcData.name.isEmpty()) {
                entity.setCustomName(Text.literal(npcData.name));
                entity.setCustomNameVisible(true);
            }

            // Apply stats if it's a living entity
            if (entity instanceof LivingEntity living) {
                applyStats(living, npcData.stats);
            }

            // Make glowing if needed
            if (npcData.stats.glowing) {
                entity.setGlowing(true);
            }
        });

        // Spawn the entity
        if (world.spawnEntity(entity)) {
            Scrubians.logger("info", "[Scrubians] Spawned violent NPC #" + npcId +
                    " (" + entityTypeString + ") at " + String.format("(%.1f, %.1f, %.1f)",
                    spawnPos.x, spawnPos.y, spawnPos.z));
            return entity;
        }

        return null;
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
            entity.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(stats.speed);
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
     * Check if an entity is a violent NPC by reading its custom data
     *
     * @param entity the entity
     * @return the boolean
     */
    public static boolean isViolentNpc(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound nbt = customData.copyNbt();
        return nbt.contains(NPC_ID_TAG);
    }

    /**
     * Get the NPC ID from an entity's custom data
     *
     * @param entity the entity
     * @return the npc id wrapped in Optional, or Optional.empty() if not found
     */
    public static Optional<Integer> getNpcId(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return Optional.empty();

        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(NPC_ID_TAG)) return Optional.empty();

        return nbt.getInt(NPC_ID_TAG);
    }

    /**
     * Get the base entity type from an entity's custom data
     *
     * @param entity the entity
     * @return the base type wrapped in Optional, or Optional.empty() if not found
     */
    public static Optional<String> getBaseType(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return Optional.empty();

        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(BASE_TYPE_TAG)) return Optional.empty();

        return nbt.getString(BASE_TYPE_TAG);
    }

    /**
     * Update stats for an already-spawned entity
     *
     * @param entity the entity
     * @param stats  the stats
     */
    public static void updateEntityStats(Entity entity, ViolentNpcRegistry.Stats stats) {
        if (entity instanceof LivingEntity living) {
            applyStats(living, stats);
        }
    }

    /**
     * Tick fire immunity.
     *
     * @param world the world
     */
    public static void tickFireImmunity(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (isViolentNpc(entity)) {
                entity.setFireTicks(0); // Constantly clear fire
            }
        }
    }
}