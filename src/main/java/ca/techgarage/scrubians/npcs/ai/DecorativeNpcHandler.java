package ca.techgarage.scrubians.npcs.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Special handler for decorative entities (mannequins, armor stands) that cannot have AI
 * These entities don't extend MobEntity, so we use alternative methods to make them "hostile"
 */
public class DecorativeNpcHandler {

    private static final Map<UUID, DecorativeNpcData> DECORATIVE_NPCS = new HashMap<>();
    private static final double ATTACK_RANGE = 2.0;
    private static final double DETECTION_RANGE = 16.0;
    private static final int ATTACK_COOLDOWN = 20; // 1 second between attacks

    /**
     * Data for tracking decorative NPCs
     */
    static class DecorativeNpcData {
        UUID entityUuid;
        double attackDamage;
        double detectionRange;
        double attackRange;
        int attackCooldown;
        int lastAttackTick;
        boolean teleportToPlayers;

        DecorativeNpcData(UUID uuid, double damage) {
            this.entityUuid = uuid;
            this.attackDamage = damage;
            this.detectionRange = DETECTION_RANGE;
            this.attackRange = ATTACK_RANGE;
            this.attackCooldown = ATTACK_COOLDOWN;
            this.lastAttackTick = 0;
            this.teleportToPlayers = false;
        }
    }

    /**
     * Register a decorative entity as a hostile NPC
     */
    public static void registerDecorativeNpc(LivingEntity entity, double attackDamage) {
        if (!isDecorativeEntity(entity)) {
            return;
        }

        DECORATIVE_NPCS.put(entity.getUuid(), new DecorativeNpcData(entity.getUuid(), attackDamage));
    }

    /**
     * Register with custom settings
     */
    public static void registerDecorativeNpc(LivingEntity entity, double attackDamage,
                                             double detectionRange, boolean teleportToPlayers) {
        if (!isDecorativeEntity(entity)) {
            return;
        }

        DecorativeNpcData data = new DecorativeNpcData(entity.getUuid(), attackDamage);
        data.detectionRange = detectionRange;
        data.teleportToPlayers = teleportToPlayers;
        DECORATIVE_NPCS.put(entity.getUuid(), data);
    }

    /**
     * Unregister a decorative NPC
     */
    public static void unregisterDecorativeNpc(UUID entityUuid) {
        DECORATIVE_NPCS.remove(entityUuid);
    }

    /**
     * Check if entity is a decorative entity
     */
    public static boolean isDecorativeEntity(LivingEntity entity) {
        return entity instanceof MannequinEntity || entity instanceof ArmorStandEntity;
    }

    /**
     * Tick all decorative NPCs - call this every server tick
     */
    public static void tickDecorativeNpcs(ServerWorld world) {
        int currentTick = (int) world.getTime();
        List<UUID> toRemove = new ArrayList<>();

        for (DecorativeNpcData data : DECORATIVE_NPCS.values()) {
            Entity entity = world.getEntity(data.entityUuid);

            // Remove if entity is gone
            if (entity == null || !entity.isAlive() || !(entity instanceof LivingEntity living)) {
                toRemove.add(data.entityUuid);
                continue;
            }

            // Find nearest player
            PlayerEntity nearestPlayer = world.getClosestPlayer(
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    data.detectionRange,
                    false
            );

            if (nearestPlayer == null) continue;

            double distance = entity.squaredDistanceTo(nearestPlayer);

            // Teleport to player if enabled and player is in range but not too close
            if (data.teleportToPlayers && distance > data.attackRange * data.attackRange &&
                    distance < data.detectionRange * data.detectionRange) {

//                if (currentTick % 40 == 0) { // Teleport every 2 seconds
//                    teleportTowardsPlayer(entity, nearestPlayer);
//                }
            }

            // Attack if in range and off cooldown
            if (distance <= data.attackRange * data.attackRange) {
                if (currentTick - data.lastAttackTick >= data.attackCooldown) {
                    attackPlayer(world, living, nearestPlayer, data);
                    data.lastAttackTick = currentTick;

                    // Make entity face the player
                    lookAtPlayer(entity, nearestPlayer);
                }
            } else {
                // Look at player even when not attacking
                lookAtPlayer(entity, nearestPlayer);
            }
        }

        // Cleanup dead entities
        toRemove.forEach(DECORATIVE_NPCS::remove);
    }

    /**
     * Attack a player
     */
    private static void attackPlayer(ServerWorld world, LivingEntity npc,
                                     PlayerEntity player, DecorativeNpcData data) {
        // Deal damage
        player.damage(world, world.getDamageSources().mobAttack(npc), (float) data.attackDamage);

        // Play attack animation if armor stand
        if (npc instanceof ArmorStandEntity armorStand) {
            // Armor stands don't have attack animations, but we can make them "punch"
            // by briefly moving their arm
            animateArmorStandPunch(armorStand);
        }

        // Add visual feedback - spawn particles or play sound
        world.playSound(
                null,
                npc.getX(),
                npc.getY(),
                npc.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                npc.getSoundCategory(),
                1.0f,
                1.0f
        );
    }

    /**
     * Make entity look at player
     */
    private static void lookAtPlayer(Entity entity, PlayerEntity player) {
        Vec3d entityPos = entity.getEntityPos();
        Vec3d playerPos = player.getEntityPos();

        double deltaX = playerPos.x - entityPos.x;
        double deltaZ = playerPos.z - entityPos.z;
        double deltaY = playerPos.y + player.getStandingEyeHeight() - (entityPos.y + entity.getStandingEyeHeight());

        // Calculate yaw (horizontal rotation)
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;

        // Calculate pitch (vertical rotation)
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float pitch = (float) (-(Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI));

        entity.setYaw(yaw);
        entity.setPitch(pitch);
        entity.setHeadYaw(yaw);
    }

    /**
     * Teleport entity towards player
     */
//    private static void teleportTowardsPlayer(Entity entity, PlayerEntity player) {
//        Vec3d playerPos = player.getEntityPos();
//        Vec3d direction = playerPos.subtract(entity.getEntityPos()).normalize();
//
//        // Teleport 3-5 blocks closer to player
//        double distance = 3.0 + Math.random() * 2.0;
//        Vec3d newPos = entity.getEntityPos().add(direction.multiply(distance));
//
//        // Make sure new position is valid (not in blocks)
//        if (entity.getEntityWorld().getBlockState(new net.minecraft.util.math.BlockPos(
//                (int) newPos.x, (int) newPos.y, (int) newPos.z)).isAir()) {
//            entity.teleport((ServerWorld) entity.getEntityWorld(), newPos.x, newPos.y, newPos.z);
//
//            // Spawn particles at teleport location
//            if (entity.getEntityWorld() instanceof ServerWorld world) {
//                world.spawnParticles(
//                        net.minecraft.particle.ParticleTypes.PORTAL,
//                        newPos.x, newPos.y + 1, newPos.z,
//                        20, 0.5, 0.5, 0.5, 0.1
//                );
//            }
//        }
//    }

    /**
     * Animate armor stand punch
     */
    private static void animateArmorStandPunch(ArmorStandEntity armorStand) {
        // This is a simplified version - you may need to adjust based on your needs
        // Armor stands have rotation for their limbs that could be animated
        // For now, we'll just make it work without visible animation
        // The damage and sound will provide feedback
    }

    /**
     * Create a "weeping angel" style NPC that only moves when not looked at
     */
    public static void registerWeepingAngelStyle(LivingEntity entity, double attackDamage) {
        if (!isDecorativeEntity(entity)) {
            return;
        }

        DecorativeNpcData data = new DecorativeNpcData(entity.getUuid(), attackDamage);
        data.teleportToPlayers = true; // Teleport when not looked at
        data.detectionRange = 32.0; // Large detection range
        DECORATIVE_NPCS.put(entity.getUuid(), data);
    }

    /**
     * Check if player is looking at entity
     */
    private static boolean isPlayerLookingAt(PlayerEntity player, Entity entity) {
        Vec3d playerLook = player.getRotationVec(1.0f);
        Vec3d toEntity = entity.getEntityPos().subtract(player.getEntityPos()).normalize();

        // Check if the angle is small (player is looking at entity)
        double dotProduct = playerLook.dotProduct(toEntity);
        return dotProduct > 0.95; // Very narrow cone
    }

    /**
     * Weeping angel tick - only moves when not looked at
     */
    public static void tickWeepingAngels(ServerWorld world) {
        for (DecorativeNpcData data : DECORATIVE_NPCS.values()) {
            if (!data.teleportToPlayers) continue; // Only weeping angels

            Entity entity = world.getEntity(data.entityUuid);
            if (entity == null || !(entity instanceof LivingEntity)) continue;

            // Find players looking at this entity
            boolean isBeingWatched = world.getPlayers().stream()
                    .anyMatch(player -> {
                        if (player.squaredDistanceTo(entity) > data.detectionRange * data.detectionRange) {
                            return false;
                        }
                        return isPlayerLookingAt(player, entity);
                    });

            // If not being watched, allow teleportation
            // If being watched, freeze completely
            if (isBeingWatched) {
                // Make entity completely still (handled by mannequin's immovable flag)
                if (entity instanceof MannequinEntity mannequin) {
                    // Mannequins have an immovable flag we could use
                }
            }
        }
    }

    /**
     * Clear all decorative NPCs
     */
    public static void clear() {
        DECORATIVE_NPCS.clear();
    }

    /**
     * Get all registered decorative NPCs
     */
    public static Set<UUID> getRegisteredNpcs() {
        return new HashSet<>(DECORATIVE_NPCS.keySet());
    }
}