package ca.techgarage.scrubians.npcs.ai;

import ca.techgarage.scrubians.mixin.MobEntityAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EntityType;

/**
 * Safely manages hostile AI for Scrubians NPCs
 */
public final class NpcAiGoalManager {

    private NpcAiGoalManager() {}

    /* ------------------------------------------------------------ */
    /*  PUBLIC API                                                   */
    /* ------------------------------------------------------------ */

    public static boolean applyBestAi(LivingEntity entity) {
        if (!(entity instanceof MobEntity mob)) return false;
        if (isDecorativeEntity(entity)) return false;

        if (isFlyingType(mob)) {
            return applyFlyingHostileAi(mob);
        }

        if (mob instanceof RangedAttackMob) {
            return applyRangedAi(mob, 15.0f, 40);
        }

        return applyMeleeHostileAi(mob);
    }

    public static boolean supportsAi(LivingEntity entity) {
        return entity instanceof MobEntity && !isDecorativeEntity(entity);
    }

    public static boolean isDecorativeEntity(LivingEntity entity) {
        return entity instanceof ArmorStandEntity ||
                entity instanceof MannequinEntity;
    }

    /* ------------------------------------------------------------ */
    /*  MELEE AI                                                     */
    /* ------------------------------------------------------------ */

    private static boolean applyMeleeHostileAi(MobEntity mob) {
        MobEntityAccessor access = (MobEntityAccessor) mob;
        clearConflictingGoals(access);

        if (mob instanceof PathAwareEntity pathMob) {
            access.scrubians$getGoalSelector()
                    .add(2, new MeleeAttackGoal(pathMob, 1.0, true));
            access.scrubians$getGoalSelector()
                    .add(7, new WanderAroundFarGoal(pathMob, 0.8));
        }

        addCommonGoals(mob, access);
        addTargeting(mob, access);
        return true;
    }

    /* ------------------------------------------------------------ */
    /*  RANGED AI                                                    */
    /* ------------------------------------------------------------ */

    private static boolean applyRangedAi(MobEntity mob, float range, int interval) {
        MobEntityAccessor access = (MobEntityAccessor) mob;
        clearConflictingGoals(access);

        if (mob instanceof RangedAttackMob ranged) {
            access.scrubians$getGoalSelector().add(
                    2, new ProjectileAttackGoal(ranged, 1.0, interval, range)
            );
        }

        if (mob instanceof PathAwareEntity pathMob) {
            access.scrubians$getGoalSelector()
                    .add(7, new WanderAroundFarGoal(pathMob, 0.6));
        }

        addCommonGoals(mob, access);
        addTargeting(mob, access);
        return true;
    }

    /* ------------------------------------------------------------ */
    /*  FLYING AI                                                    */
    /* ------------------------------------------------------------ */

    private static boolean applyFlyingHostileAi(MobEntity mob) {
        MobEntityAccessor access = (MobEntityAccessor) mob;
        clearConflictingGoals(access);

        access.scrubians$getGoalSelector()
                .add(2, new FlyingAttackGoal(mob, 1.2));
        access.scrubians$getGoalSelector()
                .add(8, new LookAroundGoal(mob));

        addTargeting(mob, access);
        return true;
    }

    /* ------------------------------------------------------------ */
    /*  GOAL HELPERS                                                 */
    /* ------------------------------------------------------------ */

    private static void addCommonGoals(MobEntity mob, MobEntityAccessor access) {
        access.scrubians$getGoalSelector()
                .add(5, new LookAtEntityGoal(mob, PlayerEntity.class, 8.0f));
        access.scrubians$getGoalSelector()
                .add(6, new LookAroundGoal(mob));
    }

    private static void addTargeting(MobEntity mob, MobEntityAccessor access) {
        access.scrubians$getTargetSelector()
                .clear(goal -> goal instanceof ActiveTargetGoal);

        access.scrubians$getTargetSelector().add(
                1, new ActiveTargetGoal<>(mob, PlayerEntity.class, true)
        );

        if (mob instanceof PathAwareEntity pathMob) {
            access.scrubians$getTargetSelector()
                    .add(2, new RevengeGoal(pathMob));
        }
    }

    private static void clearConflictingGoals(MobEntityAccessor access) {
        access.scrubians$getGoalSelector().clear(goal ->
                goal instanceof FleeEntityGoal ||
                        goal instanceof TemptGoal
        );
    }

    /* ------------------------------------------------------------ */
    /*  ENTITY TYPE CHECKS                                           */
    /* ------------------------------------------------------------ */

    private static boolean isFlyingType(MobEntity mob) {
        EntityType<?> type = mob.getType();

        return type == EntityType.PHANTOM ||
                type == EntityType.BAT ||
                type == EntityType.GHAST ||
                type == EntityType.VEX ||
                type == EntityType.BLAZE;
    }

    /* ------------------------------------------------------------ */
    /*  CUSTOM FLYING GOAL                                           */
    /* ------------------------------------------------------------ */

    private static final class FlyingAttackGoal extends Goal {
        private final MobEntity mob;
        private final double speed;

        FlyingAttackGoal(MobEntity mob, double speed) {
            this.mob = mob;
            this.speed = speed;
        }

        @Override
        public boolean canStart() {
            return mob.getTarget() != null;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) return;

            mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            mob.getNavigation().startMovingTo(target, speed);
        }
    }
}
