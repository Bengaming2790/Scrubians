package ca.techgarage.scrubians.npcs.boss;

import ca.techgarage.scrubians.npcs.boss.CustomBossPlugin;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Manages wave-based boss encounters
 */
public class BossWaveManager {

    private static final String CURRENT_WAVE_TAG = "BossCurrentWave";
    private static final String WAVE_TIMER_TAG = "BossWaveTimer";
    private static final String WAVE_ACTIVE_TAG = "BossWaveActive";

    private static final Map<Integer, WaveState> ACTIVE_WAVES = new HashMap<>();

    /**
     * State tracker for a boss's wave progression
     */
    static class WaveState {
        int npcId;
        int currentWave;
        int waveTimer;
        boolean waveActive;
        Set<UUID> activeMinions;

        WaveState(int npcId) {
            this.npcId = npcId;
            this.currentWave = 0;
            this.waveTimer = 0;
            this.waveActive = false;
            this.activeMinions = new HashSet<>();
        }
    }

    /**
     * Initialize wave system for a boss
     */
    public static void initializeWaves(Entity bossEntity, int npcId) {
        BossNpcRegistry.getNpcById(Optional.of(npcId)).ifPresent(npcData -> {
            if (npcData.waveConfig == null || npcData.waveConfig.waves.isEmpty()) return;

            ACTIVE_WAVES.put(npcId, new WaveState(npcId));

            // Mark entity with wave data
            NbtComponent customData = bossEntity.get(DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                nbt.putInt(CURRENT_WAVE_TAG, 0);
                nbt.putInt(WAVE_TIMER_TAG, 0);
                nbt.putBoolean(WAVE_ACTIVE_TAG, false);
                bossEntity.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            }

            // Start first wave immediately
            startWave(bossEntity.getEntityWorld(), bossEntity, npcData, 0);
        });
    }

    /**
     * Tick wave system
     */
    public static void tickWaves(ServerWorld world) {
        List<Integer> toRemove = new ArrayList<>();

        for (WaveState state : ACTIVE_WAVES.values()) {
            BossNpcRegistry.getNpcById(Optional.of(state.npcId)).ifPresent(npcData -> {
                if (npcData.waveConfig == null) return;

                // Find boss entity
                Optional<Entity> bossOpt = findBossEntity(world, state.npcId);
                if (bossOpt.isEmpty()) {
                    toRemove.add(state.npcId);
                    return;
                }

                Entity boss = bossOpt.get();

                // Clean up dead minions
                state.activeMinions.removeIf(uuid -> {
                    Entity minion = world.getEntity(uuid);
                    return minion == null || !minion.isAlive();
                });

                // Check if wave is complete
                if (state.waveActive && state.activeMinions.isEmpty()) {
                    state.waveActive = false;
                    state.waveTimer = getCurrentWave(npcData, state.currentWave)
                            .map(w -> w.delayBeforeNextWave)
                            .orElse(100);

                    // Show wave complete message
                    world.getPlayers().forEach(player ->
                            player.sendMessage(
                                    Text.literal("§a§lWave " + (state.currentWave + 1) + " Complete!"),
                                    false
                            )
                    );
                }

                // Count down to next wave
                if (!state.waveActive && state.waveTimer > 0) {
                    state.waveTimer--;

                    if (state.waveTimer == 0) {
                        int nextWave = state.currentWave + 1;

                        if (nextWave < npcData.waveConfig.waves.size()) {
                            // Start next wave
                            startWave(world, boss, npcData, nextWave);
                            state.currentWave = nextWave;
                        } else if (npcData.waveConfig.loopWaves) {
                            // Loop back to wave 0
                            startWave(world, boss, npcData, 0);
                            state.currentWave = 0;
                        }
                    }
                }
            });
        }

        // Remove completed wave states
        toRemove.forEach(ACTIVE_WAVES::remove);
    }

    /**
     * Start a specific wave
     */
    private static void startWave(net.minecraft.world.World world, Entity boss,
                                  BossNpcRegistry.BossNpcData npcData, int waveIndex) {
        if (!(world instanceof ServerWorld serverWorld)) return;

        Optional<BossNpcRegistry.WaveConfig.Wave> waveOpt = getCurrentWave(npcData, waveIndex);
        if (waveOpt.isEmpty()) return;

        BossNpcRegistry.WaveConfig.Wave wave = waveOpt.get();
        WaveState state = ACTIVE_WAVES.get(npcData.id);
        if (state == null) return;

        state.waveActive = true;
        state.activeMinions.clear();

        // Show wave message
        if (wave.waveMessage != null && !wave.waveMessage.isEmpty()) {
            serverWorld.getPlayers().forEach(player ->
                    player.sendMessage(Text.literal(wave.waveMessage), false)
            );
        }

        // Apply boss stat overrides
        if (wave.bossStatsOverride != null && boss instanceof LivingEntity living) {
            BossNpcEntity.updateEntityStats(boss, wave.bossStatsOverride);
        }

        // Spawn minions
        for (BossNpcRegistry.WaveConfig.MinionSpawn minionSpawn : wave.minions) {
            spawnMinions(serverWorld, minionSpawn, state);
        }

        // Notify custom behaviors
        if (boss instanceof LivingEntity living) {
            String bossType = BossNpcEntity.getBaseType(boss).orElse("");
            CustomBossPlugin.getBehavior(bossType).ifPresent(behavior ->
                    behavior.onWaveChange(living, waveIndex + 1)
            );
        }

        // Update entity NBT
        NbtComponent customData = boss.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            nbt.putInt(CURRENT_WAVE_TAG, waveIndex);
            nbt.putBoolean(WAVE_ACTIVE_TAG, true);
            boss.setComponent(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    /**
     * Spawn wave minions
     */
    private static void spawnMinions(ServerWorld world,
                                     BossNpcRegistry.WaveConfig.MinionSpawn minionSpawn,
                                     WaveState state) {
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.tryParse(minionSpawn.entityType));
        if (entityType == null) return;

        for (int i = 0; i < minionSpawn.count; i++) {
            Vec3d spawnPos = minionSpawn.spawnArea != null
                    ? minionSpawn.spawnArea.getRandomPosition()
                    : new Vec3d(0, 64, 0); // Fallback

            Entity minion = entityType.create(world, SpawnReason.MOB_SUMMONED);
            if (minion != null) {
                minion.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);

                // Apply custom stats to minion
                if (minion instanceof LivingEntity living) {
                    if (minionSpawn.stats != null) {
                        applyMinionStats(living, minionSpawn.stats);
                    }
                    // IMPORTANT: Make minions hostile to players
                    ca.techgarage.scrubians.npcs.ai.NpcAiGoalManager.applyBestAi(living);
                }

                if (world.spawnEntity(minion)) {
                    state.activeMinions.add(minion.getUuid());
                }
            }
        }
    }

    /**
     * Apply stats to minion
     */
    private static void applyMinionStats(LivingEntity minion, BossNpcRegistry.Stats stats) {
        if (minion.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH) != null) {
            minion.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH)
                    .setBaseValue(stats.health);
            minion.setHealth((float) stats.health);
        }

        if (minion.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE) != null) {
            minion.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE)
                    .setBaseValue(stats.attackDamage);
        }

        if (minion.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED) != null) {
            minion.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED)
                    .setBaseValue(stats.speed);
        }
    }

    /**
     * Find boss entity by NPC ID
     */
    private static Optional<Entity> findBossEntity(ServerWorld world, int npcId) {
        for (Entity entity : world.iterateEntities()) {
            if (BossNpcEntity.isBossNpc(entity)) {
                Optional<Integer> id = BossNpcEntity.getNpcId(entity);
                if (id.isPresent() && id.get() == npcId) {
                    return Optional.of(entity);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Get current wave configuration
     */
    private static Optional<BossNpcRegistry.WaveConfig.Wave> getCurrentWave(
            BossNpcRegistry.BossNpcData npcData, int waveIndex) {
        if (npcData.waveConfig == null ||
                waveIndex < 0 ||
                waveIndex >= npcData.waveConfig.waves.size()) {
            return Optional.empty();
        }
        return Optional.of(npcData.waveConfig.waves.get(waveIndex));
    }

    /**
     * Get current wave number for a boss
     */
    public static int getCurrentWave(int npcId) {
        WaveState state = ACTIVE_WAVES.get(npcId);
        return state != null ? state.currentWave : -1;
    }

    /**
     * Force skip to next wave
     */
    public static void skipToNextWave(ServerWorld world, int npcId) {
        WaveState state = ACTIVE_WAVES.get(npcId);
        if (state == null) return;

        // Kill all current minions
        for (UUID minionUuid : state.activeMinions) {
            Entity minion = world.getEntity(minionUuid);
            if (minion != null) {
                minion.discard();
            }
        }

        state.activeMinions.clear();
        state.waveTimer = 0;
        state.waveActive = false;
    }

    /**
     * Clear wave state for a boss
     */
    public static void clearWaveState(int npcId) {
        ACTIVE_WAVES.remove(npcId);
    }

    /**
     * Clear all wave states
     */
    public static void clearAll() {
        ACTIVE_WAVES.clear();
    }
}