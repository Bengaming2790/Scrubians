package ca.techgarage.scrubians.npcs.violent;

import ca.techgarage.scrubians.Scrubians;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side only violent NPC spawner
 * Uses vanilla entity types so no client-side mod is required
 */
public class ViolentNpcEntity {

    private static final String NPC_ID_TAG = "ScrubianViolentNpcId";
    private static final String BASE_TYPE_TAG = "ScrubianBaseType";
    private static final String IS_AI_ENTITY_TAG = "ScrubianIsAiEntity";
    private static final String IS_DISPLAY_ENTITY_TAG = "ScrubianIsDisplayEntity";
    private static final String LINKED_ENTITY_UUID_MOST_TAG = "ScrubianLinkedEntityUuidMost";
    private static final String LINKED_ENTITY_UUID_LEAST_TAG = "ScrubianLinkedEntityUuidLeast";

    // Track AI entity -> Display entity relationships
    private static final Map<UUID, UUID> AI_TO_DISPLAY = new HashMap<>();
    private static final Map<UUID, UUID> DISPLAY_TO_AI = new HashMap<>();

    /**
     * Spawn a violent NPC using a vanilla entity type
     * Automatically uses zombie AI if the entity is passive or a mannequin
     *
     * @param world            The server world
     * @param npcId            The NPC ID from registry
     * @param entityTypeString The vanilla entity type (e.g., "zombie", "skeleton", "mannequin")
     * @param spawnPos         Position to spawn at
     * @return The spawned entity, or null if failed
     */
    public static Entity spawnViolentNpc(ServerWorld world, int npcId, String entityTypeString, Vec3d spawnPos) {
        // Parse the entity type
        Identifier typeId = entityTypeString.contains(":")
                ? Identifier.tryParse(entityTypeString)
                : Identifier.ofVanilla(entityTypeString);

        EntityType<?> entityType = Registries.ENTITY_TYPE.get(typeId);

        // Check if we need to use hybrid mode (zombie AI + visual display)
        boolean needsHybridMode = isPassiveOrDecorative(world, entityType);

        if (needsHybridMode) {

            if (!Scrubians.DEVELOPER_MODE) {
                Scrubians.logger("info", "[Scrubians] Using hybrid NPC not avalible for release use");
                return null;
            } else {
                return spawnHybridNpc(world, npcId, entityType, entityTypeString, spawnPos);
            }
        } else {
            return spawnStandardNpc(world, npcId, entityType, entityTypeString, spawnPos);
        }
    }

    /**
     * Check if entity type needs hybrid mode (zombie AI + display entity)
     */
    private static boolean isPassiveOrDecorative(ServerWorld world, EntityType<?> entityType) {
        // Create a temporary entity to check its type
        Entity tempEntity = entityType.create(world, SpawnReason.COMMAND);
        if (tempEntity == null) return false;

        boolean isPassiveOrDecorative = tempEntity instanceof PassiveEntity
                || tempEntity instanceof MannequinEntity
                || tempEntity instanceof ArmorStandEntity;

        tempEntity.discard();
        return isPassiveOrDecorative;
    }

    /**
     * Spawn a hybrid NPC: Invisible zombie for AI riding on visible display entity
     */
    private static Entity spawnHybridNpc(ServerWorld world, int npcId, EntityType<?> displayType,
                                         String displayTypeString, Vec3d spawnPos) {

        if (!Scrubians.DEVELOPER_MODE) {
            Scrubians.logger("info", "[Scrubians] Hybrid NPC spawning not avalible for release use");
            return null;
        }

        Scrubians.logger("info", "[Scrubians] Spawning hybrid NPC: zombie AI with " + displayTypeString + " display");

        // Create invisible zombie for AI
        ZombieEntity zombie = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
        if (zombie == null) {
            Scrubians.logger("info", "[Scrubians] Failed to create zombie AI entity");
            return null;
        }

        // Create display entity
        Entity displayEntity = displayType.create(world, SpawnReason.COMMAND);
        if (displayEntity == null) {
            zombie.discard();
            Scrubians.logger("info", "[Scrubians] Failed to create display entity");
            return null;
        }

        // Position both at spawn location
        zombie.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);
        displayEntity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);

        // Get NPC data first (we need it for naming)
        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt = ViolentNpcRegistry.getNpcById(Optional.of(npcId));
        if (npcDataOpt.isEmpty()) {
            zombie.discard();
            displayEntity.discard();
            return null;
        }
        ViolentNpcRegistry.ViolentNpcData npcData = npcDataOpt.get();

        // Determine name for zombie (use NPC name or entity type)
        String zombieName = (npcData.name != null && !npcData.name.isEmpty())
                ? npcData.name
                : displayTypeString;

        // Make zombie invisible, silent, and tiny
        zombie.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));
        zombie.setSilent(true);

        // Scale zombie down to minimum size
        if (zombie.getAttributeInstance(EntityAttributes.SCALE) != null) {
            zombie.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(0.0625); // 1/16th size
        }

        // Set zombie's name to match the NPC
        zombie.setCustomName(Text.literal(zombieName));
        zombie.setCustomNameVisible(false); // Don't show zombie's name, display entity will show it

        // Apply stats to zombie
        applyStats(zombie, npcData.stats);

        // Apply AI to zombie
        if (!ca.techgarage.scrubians.npcs.ai.NpcAiGoalManager.applyBestAi(zombie)) {
            Scrubians.logger("info", "[Scrubians] Warning: Failed to apply AI to zombie");
        }

        // Mark zombie as AI entity
        NbtCompound zombieNbt = new NbtCompound();
        zombieNbt.putInt(NPC_ID_TAG, npcId);
        zombieNbt.putString(BASE_TYPE_TAG, "zombie_ai");
        zombieNbt.putBoolean(IS_AI_ENTITY_TAG, true);
        zombie.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(zombieNbt));

        // Mark display entity
        NbtCompound displayNbt = new NbtCompound();
        displayNbt.putInt(NPC_ID_TAG, npcId);
        displayNbt.putString(BASE_TYPE_TAG, displayTypeString);
        displayNbt.putBoolean(IS_DISPLAY_ENTITY_TAG, true);
        displayEntity.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(displayNbt));

        // Set name on display entity (visible to players)
        displayEntity.setCustomName(Text.literal(zombieName));
        displayEntity.setCustomNameVisible(true);

        // Make display entity VULNERABLE so it can be hit
        if (displayEntity instanceof LivingEntity livingDisplay) {
            livingDisplay.setInvulnerable(false);

            // Set display entity health to match zombie
            livingDisplay.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(npcData.stats.health);
            livingDisplay.setHealth((float) npcData.stats.health);
        }

        // Make display entity glow if needed
        if (npcData.stats.glowing) {
            displayEntity.setGlowing(true);
        }

        // Spawn display entity first (it's the vehicle)
        if (!world.spawnEntity(displayEntity)) {
            zombie.discard();
            return null;
        }

        // Spawn zombie second (it's the passenger)
        if (!world.spawnEntity(zombie)) {
            displayEntity.discard();
            return null;
        }

        // Make zombie ride on top of display entity
        zombie.startRiding(displayEntity);

        // Track the relationship
        AI_TO_DISPLAY.put(zombie.getUuid(), displayEntity.getUuid());
        DISPLAY_TO_AI.put(displayEntity.getUuid(), zombie.getUuid());

        // Update NBT with linked UUIDs (store as two longs)
        UUID displayUuid = displayEntity.getUuid();
        zombieNbt.putLong(LINKED_ENTITY_UUID_MOST_TAG, displayUuid.getMostSignificantBits());
        zombieNbt.putLong(LINKED_ENTITY_UUID_LEAST_TAG, displayUuid.getLeastSignificantBits());
        zombie.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(zombieNbt));

        UUID zombieUuid = zombie.getUuid();
        displayNbt.putLong(LINKED_ENTITY_UUID_MOST_TAG, zombieUuid.getMostSignificantBits());
        displayNbt.putLong(LINKED_ENTITY_UUID_LEAST_TAG, zombieUuid.getLeastSignificantBits());
        displayEntity.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(displayNbt));

        Scrubians.logger("info", "[Scrubians] Successfully spawned hybrid NPC with zombie AI riding display entity");

        // Return the zombie (AI entity) as the "main" entity for tracking
        return zombie;
    }

    /**
     * Spawn a standard NPC (entity handles both AI and display)
     */
    private static Entity spawnStandardNpc(ServerWorld world, int npcId, EntityType<?> entityType,
                                           String entityTypeString, Vec3d spawnPos) {
        // Create the entity
        Entity entity = entityType.create(world, SpawnReason.COMMAND);

        if (entity == null) {
            Scrubians.logger("info", "[Scrubians] Failed to create entity of type: " + entityTypeString);
            return null;
        }

        // Set position
        entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);

        // Mark entity with NPC ID
        NbtCompound customNbt = new NbtCompound();
        customNbt.putInt(NPC_ID_TAG, npcId);
        customNbt.putString(BASE_TYPE_TAG, entityTypeString);
        customNbt.putBoolean(IS_AI_ENTITY_TAG, false);
        customNbt.putBoolean(IS_DISPLAY_ENTITY_TAG, false);
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

                // Apply hostile AI or decorative handler
                if (!ca.techgarage.scrubians.npcs.ai.NpcAiGoalManager.applyBestAi(living)) {
                    // Entity doesn't support AI (shouldn't happen for standard mode)
                    ca.techgarage.scrubians.npcs.ai.DecorativeNpcHandler.registerDecorativeNpc(
                            living,
                            npcData.stats.attackDamage,
                            npcData.stats.followRange,
                            false
                    );

                    Scrubians.logger("info", "[Scrubians] Entity " + entityTypeString +
                            " doesn't support AI, using decorative handler");
                }
            }

            // Make glowing if needed
            if (npcData.stats.glowing) {
                entity.setGlowing(true);
            }
        });

        // Spawn the entity
        if (world.spawnEntity(entity)) {
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
     * Check if an entity is a violent NPC
     */
    public static boolean isViolentNpc(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound nbt = customData.copyNbt();
        return nbt.contains(NPC_ID_TAG);
    }

    /**
     * Check if entity is an AI entity (invisible zombie in hybrid mode)
     */
    public static boolean isAiEntity(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound nbt = customData.copyNbt();
        return nbt.getBoolean(IS_AI_ENTITY_TAG, false);
    }

    /**
     * Check if entity is a display entity (visible entity in hybrid mode)
     */
    public static boolean isDisplayEntity(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound nbt = customData.copyNbt();
        return nbt.getBoolean(IS_DISPLAY_ENTITY_TAG, false);
    }

    /**
     * Get the NPC ID from an entity
     */
    public static Optional<Integer> getNpcId(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return Optional.empty();

        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(NPC_ID_TAG)) return Optional.empty();

        return nbt.getInt(NPC_ID_TAG);
    }

    /**
     * Get the base entity type from an entity
     */
    public static Optional<String> getBaseType(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return Optional.empty();

        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(BASE_TYPE_TAG)) return Optional.empty();

        return nbt.getString(BASE_TYPE_TAG);
    }

    /**
     * Get linked entity UUID (for hybrid NPCs)
     */
    public static Optional<UUID> getLinkedEntityUuid(Entity entity) {
        NbtComponent customData = entity.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return Optional.empty();

        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(LINKED_ENTITY_UUID_MOST_TAG) || !nbt.contains(LINKED_ENTITY_UUID_LEAST_TAG)) {
            return Optional.empty();
        }

        long most = nbt.getLong(LINKED_ENTITY_UUID_MOST_TAG).orElse(-1L);
        long least = nbt.getLong(LINKED_ENTITY_UUID_LEAST_TAG).orElse(-1L);
        return Optional.of(new UUID(most, least));
    }

    /**
     * Get display entity from AI entity
     */
    public static Entity getDisplayEntity(ServerWorld world, Entity aiEntity) {
        UUID displayUuid = AI_TO_DISPLAY.get(aiEntity.getUuid());
        if (displayUuid == null) return null;
        return world.getEntity(displayUuid);
    }

    /**
     * Get AI entity from display entity
     */
    public static Entity getAiEntity(ServerWorld world, Entity displayEntity) {
        UUID aiUuid = DISPLAY_TO_AI.get(displayEntity.getUuid());
        if (aiUuid == null) return null;
        return world.getEntity(aiUuid);
    }

    /**
     * Update stats for an already-spawned entity
     */
    public static void updateEntityStats(Entity entity, ViolentNpcRegistry.Stats stats) {
        if (entity instanceof LivingEntity living) {
            applyStats(living, stats);
        }
    }

    /**
     * Tick fire immunity for all violent NPCs
     */
    public static void tickFireImmunity(ServerWorld world) {
        for (Entity entity : world.iterateEntities()) {
            if (isViolentNpc(entity)) {
                entity.setFireTicks(0);
            }
        }
    }

    /**
     * Tick hybrid NPCs to keep display entities synced and health linked
     */
    public static void tickHybridNpcs(ServerWorld world) {
        // Clean up broken links
        AI_TO_DISPLAY.entrySet().removeIf(entry -> {
            Entity aiEntity = world.getEntity(entry.getKey());
            Entity displayEntity = world.getEntity(entry.getValue());
            return aiEntity == null || displayEntity == null || !aiEntity.isAlive() || !displayEntity.isAlive();
        });

        DISPLAY_TO_AI.entrySet().removeIf(entry -> {
            Entity displayEntity = world.getEntity(entry.getKey());
            Entity aiEntity = world.getEntity(entry.getValue());
            return displayEntity == null || aiEntity == null || !displayEntity.isAlive() || !aiEntity.isAlive();
        });

        // Sync rotations and health for hybrid NPCs
        for (Map.Entry<UUID, UUID> entry : AI_TO_DISPLAY.entrySet()) {
            Entity aiEntity = world.getEntity(entry.getKey());
            Entity displayEntity = world.getEntity(entry.getValue());

            if (aiEntity != null && displayEntity != null && aiEntity.hasVehicle()) {
                // Sync yaw and pitch from zombie to display entity
                displayEntity.setYaw(aiEntity.getYaw());
                displayEntity.setPitch(aiEntity.getPitch());
                displayEntity.setHeadYaw(aiEntity.getHeadYaw());

                // Sync health bidirectionally between zombie and display entity
                if (aiEntity instanceof LivingEntity livingAi && displayEntity instanceof LivingEntity livingDisplay) {
                    float zombieHealth = livingAi.getHealth();
                    float displayHealth = livingDisplay.getHealth();

                    // Use the lower health value (if either takes damage, both reflect it)
                    float lowestHealth = Math.min(zombieHealth, displayHealth);

                    if (zombieHealth > lowestHealth) {
                        livingAi.setHealth(lowestHealth);
                    }
                    if (displayHealth > lowestHealth) {
                        livingDisplay.setHealth(lowestHealth);
                    }

                    // If either dies, kill both
                    if (lowestHealth <= 0) {
                        if (zombieHealth > 0) {
                            livingAi.setHealth(0);
                            livingAi.kill(world);
                        }
                        if (displayHealth > 0) {
                            livingDisplay.setHealth(0);
                            livingDisplay.kill(world);
                        }
                    }
                }
            }
        }
    }

    /**
     * Clear hybrid tracking maps
     */
    public static void clearHybridTracking() {
        AI_TO_DISPLAY.clear();
        DISPLAY_TO_AI.clear();
    }
}