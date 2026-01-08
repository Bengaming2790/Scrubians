package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.Scrubians;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The type Npc registry.
 */
public final class NpcRegistry {

    private static final List<NpcData> NPC_LIST = new ArrayList<>();
    private static int NEXT_ID = 0;
    private static File saveFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean needsSave = false;
    private static long lastSaveTime = 0;
    private static final long SAVE_INTERVAL_MS = 30000; // Save every 30 seconds if needed

    /**
     * The type Npc data.
     */
    public static class NpcData {

        public int id;

        public String name;

        public double x, y, z;

        public String skin;

        public List<Waypoint> path;

        public DialogueData dialogue;

        /**
         * Instantiates a new Npc data.
         * @param id       the id
         * @param name     the name
         * @param position the position
         */
        public NpcData(int id, String name, Vec3d position) {
            this.id = id;
            this.name = name;
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
            this.skin = null;
            this.path = new ArrayList<>();
            this.dialogue = null;
        }

        /**
         * Gets position.
         *
         * @return the position
         */
        public Vec3d getPosition() {
            return new Vec3d(x, y, z);
        }

        /**
         * Sets position.
         *
         * @param pos the pos
         */
        public void setPosition(Vec3d pos) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
        }

        /**
         * Gets path.
         *
         * @return the path
         */
        public List<Waypoint> getPath() {
            return path != null ? path : new ArrayList<>();
        }

        /**
         * Sets path.
         *
         * @param newPath the new path
         */
        public void setPath(List<Waypoint> newPath) {
            this.path = newPath != null ? newPath : new ArrayList<>();
        }

        /**
         * Gets dialogue.
         *
         * @return the dialogue
         */
        public DialogueData getDialogue() {
            return dialogue;
        }

        /**
         * Sets dialogue.
         *
         * @param dialogue the dialogue
         */
        public void setDialogue(DialogueData dialogue) {
            this.dialogue = dialogue;
        }
    }

    /**
     * The type Waypoint.
     */
    public static class Waypoint {

        public double x, y, z;

        public int waitTicks; // How long to wait at this waypoint (in ticks, 20 = 1 second)

        /**
         * Instantiates a new Waypoint.
         *
         * @param x         the x
         * @param y         the y
         * @param z         the z
         * @param waitTicks the wait ticks
         */
        public Waypoint(double x, double y, double z, int waitTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.waitTicks = waitTicks;
        }

        /**
         * Instantiates a new Waypoint.
         *
         * @param pos       the pos
         * @param waitTicks the wait ticks
         */
        public Waypoint(Vec3d pos, int waitTicks) {
            this(pos.x, pos.y, pos.z, waitTicks);
        }

        /**
         * To vec 3 d vec 3 d.
         *
         * @return the vec 3 d
         */
        public Vec3d toVec3d() {
            return new Vec3d(x, y, z);
        }
    }

    /**
     * The type Dialogue data.
     */
    public static class DialogueData {
        /**
         * The Pages.
         */
        public List<DialoguePageData> pages;

        /**
         * Instantiates a new Dialogue data.
         */
        public DialogueData() {
            this.pages = new ArrayList<>();
        }

        /**
         * The type Dialogue page data.
         */
        public static class DialoguePageData {

            public String text;

            public List<DialogueOptionData> options;

            public DialoguePageData() {
                this.options = new ArrayList<>();
            }
            public DialoguePageData(String text) {
                this.text = text;
                this.options = new ArrayList<>();
            }
        }

        /**
         * The type Dialogue option data.
         */
        public static class DialogueOptionData {

            public String text;

            public String action;
            public DialogueOptionData() {}

            /**
             * Instantiates a new Dialogue option data.
             *
             * @param text   the text
             * @param action the action
             */
            public DialogueOptionData(String text, String action) {
                this.text = text;
                this.action = action;
            }
        }
    }

    /**
     * Init.
     *
     * @param serverRoot the server root
     */
// Must be called once per world
    public static void init(File serverRoot) {
        // Use the actual server root directory (where the JAR is)
        // serverRoot from getRunDirectory() should already be the right place
        File scrubiansFolder = new File(serverRoot, ".scrubians");
        if (!scrubiansFolder.exists()) {
            Scrubians.logger("info","[Scrubians] Creating .scrubians folder at: " + scrubiansFolder.getAbsolutePath());
            scrubiansFolder.mkdirs();
        } else {
            Scrubians.logger("info","[Scrubians] .scrubians folder exists at: " + scrubiansFolder.getAbsolutePath());
        }

        File dataFolder = new File(scrubiansFolder, "data");
        if (!dataFolder.exists()) {
            Scrubians.logger("info","[Scrubians] Creating data folder at: " + dataFolder.getAbsolutePath());
            dataFolder.mkdirs();
        } else {
            Scrubians.logger("info","[Scrubians] data folder exists at: " + dataFolder.getAbsolutePath());
        }

        saveFile = new File(dataFolder, "scrubians_npcs.json");
        Scrubians.logger("info","[Scrubians] JSON file path: " + saveFile.getAbsolutePath());

        if (saveFile.exists()) {
            Scrubians.logger("info","[Scrubians] Loading existing NPCs from JSON...");
            try (FileReader reader = new FileReader(saveFile)) {
                Type listType = new TypeToken<List<NpcData>>() {}.getType();
                List<NpcData> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) {
                    NPC_LIST.clear();
                    NPC_LIST.addAll(loaded);
                    for (NpcData npc : NPC_LIST) {
                        if (npc.id >= NEXT_ID) NEXT_ID = npc.id + 1;
                        // Ensure path is initialized
                        if (npc.path == null) npc.path = new ArrayList<>();
                    }
                    Scrubians.logger("info","[Scrubians] Loaded " + NPC_LIST.size() + " NPCs from JSON");
                }
            } catch (Exception e) {
                Scrubians.logger("info","[Scrubians] ERROR: Corrupted JSON file detected!");
                Scrubians.logger("error","[Scrubians] Creating backup and starting fresh...");

                // Create backup of corrupted file
                File backup = new File(dataFolder, "scrubians_npcs_corrupted_" + System.currentTimeMillis() + ".json");
                try {
                    java.nio.file.Files.copy(saveFile.toPath(), backup.toPath());
                    Scrubians.logger("info","[Scrubians] Backup saved to: " + backup.getName());
                } catch (IOException backupError) {
                    Scrubians.logger("error","[Scrubians] Failed to create backup: " + backupError.getMessage());
                }

                // Start fresh
                NPC_LIST.clear();
                NEXT_ID = 0;
                save();

                Scrubians.logger("error","[Scrubians] Original error details:");
                e.printStackTrace();
            }
        } else {
            Scrubians.logger("info","[Scrubians] JSON file doesn't exist, creating new one...");
            save();
        }
    }

    private static void save() {
        if (saveFile == null) {
            Scrubians.logger("error","[Scrubians] Cannot save - saveFile is null!");
            return;
        }

        try {
            // Use Files.write for better cross-platform compatibility
            String json = GSON.toJson(NPC_LIST);
            java.nio.file.Files.write(saveFile.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Scrubians.logger("info","[Scrubians] Saved " + NPC_LIST.size() + " NPCs to: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            Scrubians.logger("error","[Scrubians] Error saving JSON file:");
            e.printStackTrace();
        }
    }

    /**
     * Register npc int.
     *
     * @param name     the name
     * @param position the position
     * @return the int
     */
    public static int registerNpc(String name, Vec3d position) {
        int id = NEXT_ID++;
        NpcData npc = new NpcData(id, name, position);
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
     * Change position.
     *
     * @param id       the id
     * @param position the position
     */
    public static void changePosition(int id, Vec3d position) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.setPosition(position);
                needsSave = true; // Mark that we need to save
                return;
            }
        }
    }

    /**
     * Periodic save check - call this from server tick
     * Only saves if data has changed and enough time has passed
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
     * Force save immediately (for shutdown or important changes)
     */
    public static void forceSave() {
        if (needsSave || !NPC_LIST.isEmpty()) {
            save();
            needsSave = false;
            lastSaveTime = System.currentTimeMillis();
        }
    }

    /**
     * Change position and save.
     *
     * @param id       the id
     * @param position the position
     */
    public static void changePositionAndSave(int id, Vec3d position) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.setPosition(position);
                forceSave();
                return;
            }
        }
    }

    /**
     * Change name.
     *
     * @param id      the id
     * @param newName the new name
     */
    public static void changeName(int id, String newName) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.name = newName;
                forceSave();
                return;
            }
        }
    }

    /**
     * Change skin.
     *
     * @param id      the id
     * @param newSkin the new skin
     */
    public static void changeSkin(int id, String newSkin) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.skin = newSkin;
                forceSave();
                return;
            }
        }
    }

    /**
     * Sets path.
     *
     * @param id   the id
     * @param path the path
     */
    public static void setPath(int id, List<Waypoint> path) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.setPath(path);
                forceSave();
                return;
            }
        }
    }

    /**
     * Add waypoint.
     *
     * @param id       the id
     * @param waypoint the waypoint
     */
    public static void addWaypoint(int id, Waypoint waypoint) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.getPath().add(waypoint);
                forceSave();
                return;
            }
        }
    }

    /**
     * Sets dialogue.
     *
     * @param id       the id
     * @param dialogue the dialogue
     */
    public static void setDialogue(int id, DialogueData dialogue) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.setDialogue(dialogue);
                forceSave();
                return;
            }
        }
    }

    /**
     * Clear path.
     *
     * @param id the id
     */
    public static void clearPath(int id) {
        for (NpcData npc : NPC_LIST) {
            if (npc.id == id) {
                npc.getPath().clear();
                forceSave();
                return;
            }
        }
    }

    /**
     * Gets all npcs.
     *
     * @return the all npcs
     */
    public static List<NpcData> getAllNpcs() {
        return List.copyOf(NPC_LIST);
    }

    /**
     * Gets npc by id.
     *
     * @param id the id
     * @return the npc by id
     */
    public static Optional<NpcData> getNpcById(int id) {
        return NPC_LIST.stream().filter(npc -> npc.id == id).findFirst();
    }

    /**
     * Clear.
     */
    public static void clear() {
        NPC_LIST.clear();
        forceSave();
    }
}