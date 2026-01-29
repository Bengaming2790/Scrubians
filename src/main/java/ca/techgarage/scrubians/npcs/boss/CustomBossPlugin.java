package ca.techgarage.scrubians.npcs.boss;

import ca.techgarage.scrubians.npcs.boss.BossNpcRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Plugin system for custom boss implementations
 * Allows other mods to register custom bosses with unique behaviors
 */
public class CustomBossPlugin {

    private static final Map<String, IBossFactory> BOSS_FACTORIES = new HashMap<>();
    private static final Map<String, IBossRenderer> BOSS_RENDERERS = new HashMap<>();
    private static final Map<String, IBossBehavior> BOSS_BEHAVIORS = new HashMap<>();

    /**
     * Register a custom boss factory
     * @param bossId Unique identifier for this boss type
     * @param factory Factory that creates the boss entity
     */
    public static void registerBossFactory(String bossId, IBossFactory factory) {
        BOSS_FACTORIES.put(bossId, factory);
    }

    /**
     * Register a custom boss renderer (for Polymer)
     * @param bossId Boss identifier
     * @param renderer Renderer implementation
     */
    public static void registerBossRenderer(String bossId, IBossRenderer renderer) {
        BOSS_RENDERERS.put(bossId, renderer);
    }

    /**
     * Register custom boss behavior
     * @param bossId Boss identifier
     * @param behavior Behavior implementation
     */
    public static void registerBossBehavior(String bossId, IBossBehavior behavior) {
        BOSS_BEHAVIORS.put(bossId, behavior);
    }

    /**
     * Create a custom boss entity
     */
    public static Entity createCustomBoss(ServerWorld world, String bossId, Vec3d pos,
                                          BossNpcRegistry.BossNpcData data) {
        IBossFactory factory = BOSS_FACTORIES.get(bossId);
        if (factory != null) {
            return factory.createBoss(world, pos, data);
        }
        return null;
    }

    /**
     * Get renderer for a boss
     */
    public static Optional<IBossRenderer> getRenderer(String bossId) {
        return Optional.ofNullable(BOSS_RENDERERS.get(bossId));
    }

    /**
     * Get behavior for a boss
     */
    public static Optional<IBossBehavior> getBehavior(String bossId) {
        return Optional.ofNullable(BOSS_BEHAVIORS.get(bossId));
    }

    /**
     * Tick all custom boss behaviors
     */
    public static void tickCustomBosses(ServerWorld world) {
        for (Map.Entry<String, IBossBehavior> entry : BOSS_BEHAVIORS.entrySet()) {
            entry.getValue().tick(world);
        }
    }

    /**
     * Get all registered boss IDs
     */
    public static Set<String> getRegisteredBosses() {
        return Collections.unmodifiableSet(BOSS_FACTORIES.keySet());
    }

    /**
     * Interface for creating boss entities
     */
    public interface IBossFactory {
        /**
         * Create a new boss entity
         * @param world The world to spawn in
         * @param pos Position to spawn at
         * @param data Boss configuration data
         * @return The created entity
         */
        Entity createBoss(ServerWorld world, Vec3d pos, BossNpcRegistry.BossNpcData data);
    }

    /**
     * Interface for custom boss rendering (Polymer support)
     */
    public interface IBossRenderer {
        /**
         * Get the model item ID for this boss
         * @return Item identifier to use for the model
         */
        String getModelItemId();

        /**
         * Get custom texture identifier
         * @return Texture identifier or null for default
         */
        String getTextureId();

        /**
         * Should use custom renderer
         * @return true if custom rendering is needed
         */
        boolean useCustomRenderer();

        /**
         * Apply custom model to entity (Polymer integration)
         * @param entity The entity to apply model to
         */
        void applyModel(Entity entity);
    }

    /**
     * Interface for custom boss behavior
     */
    public interface IBossBehavior {
        /**
         * Called every tick for this boss type
         * @param world The server world
         */
        void tick(ServerWorld world);

        /**
         * Called when boss takes damage
         * @param boss The boss entity
         * @param damage Amount of damage
         * @param source Damage source description
         * @return true to cancel default damage handling
         */
        boolean onDamage(LivingEntity boss, float damage, String source);

        /**
         * Called when boss attacks
         * @param boss The boss entity
         * @param target The attack target
         * @return true to cancel default attack
         */
        boolean onAttack(LivingEntity boss, Entity target);

        /**
         * Called when boss dies
         * @param boss The boss entity
         * @param world The server world
         */
        void onDeath(LivingEntity boss, ServerWorld world);

        /**
         * Called when wave changes (if boss has wave config)
         * @param boss The boss entity
         * @param newWave New wave number
         */
        void onWaveChange(LivingEntity boss, int newWave);
    }

    /**
     * Abstract base class for easier behavior implementation
     */
    public static abstract class AbstractBossBehavior implements IBossBehavior {
        @Override
        public void tick(ServerWorld world) {
            // Default: do nothing
        }

        @Override
        public boolean onDamage(LivingEntity boss, float damage, String source) {
            return false; // Don't cancel
        }

        @Override
        public boolean onAttack(LivingEntity boss, Entity target) {
            return false; // Don't cancel
        }

        @Override
        public void onDeath(LivingEntity boss, ServerWorld world) {
            // Default: do nothing
        }

        @Override
        public void onWaveChange(LivingEntity boss, int newWave) {
            // Default: do nothing
        }
    }
}