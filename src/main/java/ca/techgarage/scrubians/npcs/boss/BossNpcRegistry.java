package ca.techgarage.scrubians.npcs.boss;

import ca.techgarage.scrubians.Scrubians;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced Registry for Boss NPCs with wave support and advanced customization
 */
public final class BossNpcRegistry {

    private static final List<BossNpcData> NPC_LIST = new ArrayList<>();
    private static int NEXT_ID = 0;
    private static File saveFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean needsSave = false;
    private static long lastSaveTime = 0;
    private static final long SAVE_INTERVAL_MS = 30000; // 30 seconds

    public static class BossNpcData {
        public int id;
        public String name;
        public String entityType;
        public SpawnArea spawnArea;
        public Stats stats;
        public boolean persistent;
        public WaveConfig waveConfig; // NEW: Wave-based spawning
        public CustomModelConfig customModel; // NEW: Polymer model support
        public BossAbilities abilities; // NEW: Special boss abilities

        public BossNpcData(int id, String name, String entityType, SpawnArea spawnArea) {
            this.id = id;
            this.name = name;
            this.entityType = entityType;
            this.spawnArea = spawnArea;
            this.stats = new Stats();
            this.persistent = true;
            this.waveConfig = null;
            this.customModel = null;
            this.abilities = new BossAbilities();
        }

        public EntityType<?> getEntityType() {
            try {
                Identifier identifier = Identifier.tryParse(entityType);
                if (identifier != null) {
                    return Registries.ENTITY_TYPE.get(identifier);
                }
            } catch (Exception e) {
                Scrubians.logger("error","[Scrubians] Invalid entity type: " + entityType);
            }
            return EntityType.ZOMBIE;
        }
    }

    public static class SpawnArea {
        public double minX, minY, minZ;
        public double maxX, maxY, maxZ;
        public int maxCount;
        public int respawnDelayTicks;

        public SpawnArea() {
            this.maxCount = 1;
            this.respawnDelayTicks = 200;
        }

        public SpawnArea(Vec3d corner1, Vec3d corner2, int maxCount, int respawnDelayTicks) {
            this.minX = Math.min(corner1.x, corner2.x);
            this.minY = Math.min(corner1.y, corner2.y);
            this.minZ = Math.min(corner1.z, corner2.z);
            this.maxX = Math.max(corner1.x, corner2.x);
            this.maxY = Math.max(corner1.y, corner2.y);
            this.maxZ = Math.max(corner1.z, corner2.z);
            this.maxCount = maxCount;
            this.respawnDelayTicks = respawnDelayTicks;
        }

        public Vec3d getRandomPosition() {
            double x = minX + Math.random() * (maxX - minX);
            double y = minY + Math.random() * (maxY - minY);
            double z = minZ + Math.random() * (maxZ - minZ);
            return new Vec3d(x, y, z);
        }

        public boolean isInside(Vec3d pos) {
            return pos.x >= minX && pos.x <= maxX &&
                    pos.y >= minY && pos.y <= maxY &&
                    pos.z >= minZ && pos.z <= maxZ;
        }
    }

    public static class Stats {
        public double health;
        public double attackDamage;
        public double speed;
        public double knockbackResistance;
        public double followRange;
        public double sizeMultiplier;
        public double gravity; // NEW: Custom gravity multiplier
        public boolean glowing;
        public boolean invulnerable; // NEW: Invulnerability periods
        public int invulnerabilityTicks; // NEW: Ticks between damage

        public Stats() {
            this.health = 100.0;
            this.attackDamage = 5.0;
            this.speed = 0.23;
            this.knockbackResistance = 0.5;
            this.followRange = 32.0;
            this.sizeMultiplier = 2.0;
            this.gravity = 1.0;
            this.glowing = true;
            this.invulnerable = false;
            this.invulnerabilityTicks = 0;
        }

        public Stats(double health, double attackDamage, double speed, double knockbackResistance,
                     double followRange, double sizeMultiplier, double gravity) {
            this.health = health;
            this.attackDamage = attackDamage;
            this.speed = speed;
            this.sizeMultiplier = sizeMultiplier;
            this.knockbackResistance = knockbackResistance;
            this.followRange = followRange;
            this.gravity = gravity;
            this.glowing = true;
            this.invulnerable = false;
            this.invulnerabilityTicks = 0;
        }
    }

    /**
     * NEW: Wave-based spawning configuration
     */
    public static class WaveConfig {
        public List<Wave> waves;
        public boolean loopWaves; // If true, restart from wave 1 after completion
        public int currentWave; // Track current wave (0-indexed)

        public WaveConfig() {
            this.waves = new ArrayList<>();
            this.loopWaves = false;
            this.currentWave = 0;
        }

        public static class Wave {
            public int waveNumber;
            public List<MinionSpawn> minions; // Enemies spawned in this wave
            public Stats bossStatsOverride; // Optional: Change boss stats per wave
            public int delayBeforeNextWave; // Ticks to wait before next wave
            public String waveMessage; // Message displayed when wave starts

            public Wave() {
                this.minions = new ArrayList<>();
                this.delayBeforeNextWave = 100;
            }
        }

        public static class MinionSpawn {
            public String entityType;
            public int count;
            public SpawnArea spawnArea; // Where minions spawn
            public Stats stats; // Custom stats for minions

            public MinionSpawn() {
                this.count = 1;
            }
        }
    }

    /**
     * NEW: Custom model configuration for Polymer
     */
    public static class CustomModelConfig {
        public String modelId; // Custom model identifier for Polymer
        public String itemModelId; // Item model to use for the entity
        public boolean useCustomRenderer; // If true, use custom renderer
        public String textureId; // Custom texture identifier

        public CustomModelConfig() {
            this.useCustomRenderer = false;
        }
    }

    /**
     * NEW: Boss special abilities
     */
    public static class BossAbilities {
        public boolean teleportOnDamage; // Teleport when hit
        public double teleportChance; // 0.0 to 1.0
        public boolean summonMinions; // Summon enemies periodically
        public int minionSummonInterval; // Ticks between summons
        public String minionType; // Entity type to summon
        public int maxMinions; // Max minions at once
        public boolean phaseChanges; // Change stats at health thresholds
        public List<PhaseChange> phases;
        public boolean regeneration; // Regenerate health over time
        public double regenRate; // Health per second
        public boolean areaOfEffect; // Deal damage in radius
        public double aoeRadius;
        public double aoeDamage;
        public int aoeInterval; // Ticks between AOE

        public BossAbilities() {
            this.teleportOnDamage = false;
            this.teleportChance = 0.0;
            this.summonMinions = false;
            this.minionSummonInterval = 200;
            this.maxMinions = 5;
            this.phaseChanges = false;
            this.phases = new ArrayList<>();
            this.regeneration = false;
            this.regenRate = 0.0;
            this.areaOfEffect = false;
            this.aoeRadius = 5.0;
            this.aoeDamage = 2.0;
            this.aoeInterval = 40;
        }

        public static class PhaseChange {
            public double healthThreshold; // % of max health (0.0 to 1.0)
            public Stats newStats; // Stats to apply
            public String phaseMessage; // Message when phase starts
            public List<String> effectsToApply; // Status effects to apply

            public PhaseChange() {
                this.effectsToApply = new ArrayList<>();
            }
        }
    }

    // Initialization and save methods remain the same
    public static void init(File serverRoot) {
        File scrubiansFolder = new File(serverRoot, ".scrubians");
        if (!scrubiansFolder.exists()) {
            scrubiansFolder.mkdirs();
        }

        File dataFolder = new File(scrubiansFolder, "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        saveFile = new File(dataFolder, "boss_npcs.json");
        Scrubians.logger("info","[Scrubians] Boss NPC JSON file: " + saveFile.getAbsolutePath());

        if (saveFile.exists()) {
            Scrubians.logger("info","[Scrubians] Loading boss NPCs from JSON...");
            try (FileReader reader = new FileReader(saveFile)) {
                Type listType = new TypeToken<List<BossNpcData>>() {}.getType();
                List<BossNpcData> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) {
                    NPC_LIST.clear();
                    NPC_LIST.addAll(loaded);
                    for (BossNpcData npc : NPC_LIST) {
                        if (npc.id >= NEXT_ID) NEXT_ID = npc.id + 1;
                        if (npc.stats == null) npc.stats = new Stats();
                        if (npc.spawnArea == null) npc.spawnArea = new SpawnArea();
                        if (npc.abilities == null) npc.abilities = new BossAbilities();
                    }
                    Scrubians.logger("info","[Scrubians] Loaded " + NPC_LIST.size() + " boss NPCs");
                }
            } catch (Exception e) {
                Scrubians.logger("error","[Scrubians] ERROR loading boss NPCs:");
                e.printStackTrace();
                NPC_LIST.clear();
                NEXT_ID = 0;
                save();
            }
        } else {
            Scrubians.logger("info","[Scrubians] Creating new boss NPCs file...");
            save();
        }
    }

    private static void save() {
        if (saveFile == null) return;

        try {
            String json = GSON.toJson(NPC_LIST);
            java.nio.file.Files.write(saveFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Scrubians.logger("info","[Scrubians] Saved " + NPC_LIST.size() + " boss NPCs");
        } catch (IOException e) {
            Scrubians.logger("error","[Scrubians] Error saving boss NPCs:");
            e.printStackTrace();
        }
    }

    public static int registerNpc(String name, String entityType, SpawnArea spawnArea) {
        int id = NEXT_ID++;
        BossNpcData npc = new BossNpcData(id, name, entityType, spawnArea);
        NPC_LIST.add(npc);
        forceSave();
        return id;
    }

    public static void removeNpcById(int id) {
        NPC_LIST.removeIf(npc -> npc.id == id);
        forceSave();
    }

    public static void setStats(int id, Stats stats) {
        for (BossNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.stats = stats;
                forceSave();
                return;
            }
        }
    }

    public static void setAbilities(int id, BossAbilities abilities) {
        for (BossNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.abilities = abilities;
                forceSave();
                return;
            }
        }
    }

    public static void setWaveConfig(int id, WaveConfig waveConfig) {
        for (BossNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.waveConfig = waveConfig;
                forceSave();
                return;
            }
        }
    }

    public static void setCustomModel(int id, CustomModelConfig modelConfig) {
        for (BossNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.customModel = modelConfig;
                forceSave();
                return;
            }
        }
    }

    public static void setSpawnArea(int id, SpawnArea area) {
        for (BossNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.spawnArea = area;
                forceSave();
                return;
            }
        }
    }

    public static void setPersistent(int id, boolean persistent) {
        for (BossNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.persistent = persistent;
                forceSave();
                return;
            }
        }
    }

    public static void tickSave() {
        if (needsSave) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSaveTime >= SAVE_INTERVAL_MS) {
                save();
                needsSave = false;
                lastSaveTime = currentTime;
            }
        }
    }

    public static void forceSave() {
        save();
        needsSave = false;
        lastSaveTime = System.currentTimeMillis();
    }

    public static List<BossNpcData> getAllNpcs() {
        return List.copyOf(NPC_LIST);
    }

    public static Optional<BossNpcData> getNpcById(Optional<Integer> id) {
        int realID = id.orElse(-1);
        return NPC_LIST.stream().filter(npc -> npc.id == realID).findFirst();
    }

    public static void clear() {
        NPC_LIST.clear();
        forceSave();
    }
}