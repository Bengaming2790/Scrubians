package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.Scrubians;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registry for Violent NPCs - hostile entities with custom stats
 */
public final class ViolentNpcRegistry {

    private static final List<ViolentNpcData> NPC_LIST = new ArrayList<>();
    private static int NEXT_ID = 0;
    private static File saveFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean needsSave = false;
    private static long lastSaveTime = 0;
    private static final long SAVE_INTERVAL_MS = 30000; // 30 seconds

    /**
     * The type Violent npc data.
     */
    public static class ViolentNpcData {

        public int id;
        public String name;
        public String entityType; // e.g., "minecraft:zombie", "minecraft:wither"
        public SpawnArea spawnArea;
        public Stats stats;
        public boolean persistent; // If true, respawns when killed

        /**
         * Instantiates a new Violent npc data.
         *
         * @param id         the id
         * @param name       the name
         * @param entityType the entity type
         * @param spawnArea  the spawn area
         */
        public ViolentNpcData(int id, String name, String entityType, SpawnArea spawnArea) {
            this.id = id;
            this.name = name;
            this.entityType = entityType;
            this.spawnArea = spawnArea;
            this.stats = new Stats();
            this.persistent = true;
        }

        /**
         * Gets entity type.
         *
         * @return the entity type
         */
        public EntityType<?> getEntityType() {
            try {
                Identifier identifier = Identifier.tryParse(entityType);
                if (identifier != null) {
                    return Registries.ENTITY_TYPE.get(identifier);
                }
            } catch (Exception e) {
                System.err.println("[Scrubians] Invalid entity type: " + entityType);
            }
            return EntityType.ZOMBIE; // Fallback
        }
    }

    /**
     * The type Spawn area.
     */
    public static class SpawnArea {
        public double minX, minY, minZ;
        public double maxX, maxY, maxZ;
        public int maxCount; // Maximum number of this NPC that can exist at once
        public int respawnDelayTicks; // Ticks before respawning (20 = 1 second)

        /**
         * Instantiates a new Spawn area.
         */
        public SpawnArea() {
            this.maxCount = 1;
            this.respawnDelayTicks = 200; // 10 seconds default
        }

        /**
         * Instantiates a new Spawn area.
         *
         * @param corner1           the corner 1
         * @param corner2           the corner 2
         * @param maxCount          the max count
         * @param respawnDelayTicks the respawn delay ticks
         */
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

        /**
         * Gets random position.
         *
         * @return the random position
         */
        public Vec3d getRandomPosition() {
            double x = minX + Math.random() * (maxX - minX);
            double y = minY + Math.random() * (maxY - minY);
            double z = minZ + Math.random() * (maxZ - minZ);
            return new Vec3d(x, y, z);
        }

        /**
         * Is inside boolean.
         *
         * @param pos the pos
         * @return the boolean
         */
        public boolean isInside(Vec3d pos) {
            return pos.x >= minX && pos.x <= maxX &&
                    pos.y >= minY && pos.y <= maxY &&
                    pos.z >= minZ && pos.z <= maxZ;
        }
    }

    /**
     * The type Stats.
     */
    public static class Stats {
        public double health; // Max health
        public double attackDamage;
        public double speed; // Movement speed multiplier
        public double knockbackResistance; // 0.0 to 1.0
        public double followRange; // How far they detect players
        public boolean glowing; // Make entity glow

        /**
         * Instantiates a new Stats.
         */
        public Stats() {
            this.health = 20.0; // Default mob health
            this.attackDamage = 2.0;
            this.speed = 1.0;
            this.knockbackResistance = 0.0;
            this.followRange = 16.0;
            this.glowing = false;
        }

        /**
         * Instantiates a new Stats.
         *
         * @param health              the health
         * @param attackDamage        the attack damage
         * @param speed               the speed
         * @param knockbackResistance the knockback resistance
         * @param followRange         the follow range
         */
        public Stats(double health, double attackDamage, double speed, double knockbackResistance, double followRange) {
            this.health = health;
            this.attackDamage = attackDamage;
            this.speed = speed;
            this.knockbackResistance = knockbackResistance;
            this.followRange = followRange;
            this.glowing = false;
        }
    }

    /**
     * Init.
     *
     * @param serverRoot the server root
     */
    public static void init(File serverRoot) {
        File scrubiansFolder = new File(serverRoot, ".scrubians");
        if (!scrubiansFolder.exists()) {
            scrubiansFolder.mkdirs();
        }

        File dataFolder = new File(scrubiansFolder, "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        saveFile = new File(dataFolder, "violent_npcs.json");
        Scrubians.logger("info","[Scrubians] Violent NPC JSON file: " + saveFile.getAbsolutePath());

        if (saveFile.exists()) {
            Scrubians.logger("info","[Scrubians] Loading violent NPCs from JSON...");
            try (FileReader reader = new FileReader(saveFile)) {
                Type listType = new TypeToken<List<ViolentNpcData>>() {}.getType();
                List<ViolentNpcData> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) {
                    NPC_LIST.clear();
                    NPC_LIST.addAll(loaded);
                    for (ViolentNpcData npc : NPC_LIST) {
                        if (npc.id >= NEXT_ID) NEXT_ID = npc.id + 1;
                        if (npc.stats == null) npc.stats = new Stats();
                        if (npc.spawnArea == null) npc.spawnArea = new SpawnArea();
                    }
                    Scrubians.logger("info","[Scrubians] Loaded " + NPC_LIST.size() + " violent NPCs");
                }
            } catch (Exception e) {
                Scrubians.logger("error","[Scrubians] ERROR loading violent NPCs:");
                e.printStackTrace();
                NPC_LIST.clear();
                NEXT_ID = 0;
                save();
            }
        } else {
            Scrubians.logger("info","[Scrubians] Creating new violent NPCs file...");
            save();
        }
    }

    private static void save() {
        if (saveFile == null) return;

        try {
            String json = GSON.toJson(NPC_LIST);
            java.nio.file.Files.write(saveFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Scrubians.logger("info","[Scrubians] Saved " + NPC_LIST.size() + " violent NPCs");
        } catch (IOException e) {
            Scrubians.logger("error","[Scrubians] Error saving violent NPCs:");
            e.printStackTrace();
        }
    }

    /**
     * Register npc int.
     *
     * @param name       the name
     * @param entityType the entity type
     * @param spawnArea  the spawn area
     * @return the int
     */
    public static int registerNpc(String name, String entityType, SpawnArea spawnArea) {
        int id = NEXT_ID++;
        ViolentNpcData npc = new ViolentNpcData(id, name, entityType, spawnArea);
        NPC_LIST.add(npc);
        forceSave();
        return id;
    }

    /**
     * Remove npc by id.
     *
     * @param id the id
     */
    public static void removeNpcById(int id) {
        NPC_LIST.removeIf(npc -> npc.id == id);
        forceSave();
    }

    /**
     * Sets stats.
     *
     * @param id    the id
     * @param stats the stats
     */
    public static void setStats(int id, Stats stats) {
        for (ViolentNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.stats = stats;
                forceSave();
                return;
            }
        }
    }

    /**
     * Sets spawn area.
     *
     * @param id   the id
     * @param area the area
     */
    public static void setSpawnArea(int id, SpawnArea area) {
        for (ViolentNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.spawnArea = area;
                forceSave();
                return;
            }
        }
    }

    /**
     * Sets persistent.
     *
     * @param id         the id
     * @param persistent the persistent
     */
    public static void setPersistent(int id, boolean persistent) {
        for (ViolentNpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.persistent = persistent;
                forceSave();
                return;
            }
        }
    }

    /**
     * Tick save.
     */
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

    /**
     * Force save.
     */
    public static void forceSave() {
        save();
        needsSave = false;
        lastSaveTime = System.currentTimeMillis();
    }

    /**
     * Gets all npcs.
     *
     * @return the all npcs
     */
    public static List<ViolentNpcData> getAllNpcs() {
        return List.copyOf(NPC_LIST);
    }

    /**
     * Gets npc by id.
     *
     * @param id the id
     * @return the npc by id
     */
    public static Optional<ViolentNpcData> getNpcById(Optional<Integer> id) {
        int realID = id.orElse(-1);


        return NPC_LIST.stream().filter(npc -> npc.id == realID).findFirst();
    }

    /**
     * Clear.
     */
    public static void clear() {
        NPC_LIST.clear();
        forceSave();
    }
}