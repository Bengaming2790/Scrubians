package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.Scrubians;
import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class NpcReloadCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("reload")
                                .requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.reload"))
                                .executes(NpcReloadCommand::execute))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        // Load valid NPC IDs from files
        Set<Integer> validNpcIds = loadValidNpcIds();

        if (validNpcIds == null) {
            source.sendError(Text.literal("Failed to load NPC data files."));
            return 0;
        }

        int removed = 0;
        int kept = 0;

        // Iterate through all worlds and check NPCs
        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof TrackingMannequinEntity trackingNpc) {
                    int npcId = trackingNpc.getNpcId();

                    // If the NPC ID is not in the valid list, remove it
                    if (!validNpcIds.contains(npcId)) {
                        trackingNpc.discard();
                        removed++;
                        Scrubians.logger("info", "Removed NPC #" + npcId + " (not found in data files)");
                    } else {
                        kept++;
                    }
                } else if (entity instanceof MannequinEntity mannequin) {
                    // Also remove any non-tracking mannequins (invalid NPCs)
                    mannequin.discard();
                    removed++;
                }
            }
        }

        int finalRemoved = removed;
        int finalKept = kept;

        source.sendFeedback(() ->
                        Text.literal("§a[Scrubians] NPC Reload Complete:\n" +
                                "§7- Kept: §e" + finalKept + " §7valid NPC(s)\n" +
                                "§7- Removed: §c" + finalRemoved + " §7invalid NPC(s)"),
                true
        );

        Scrubians.logger("info", "NPC reload complete: " + finalKept + " kept, " + finalRemoved + " removed");

        return 1;
    }

    /**
     * Load all valid NPC IDs from the data files
     * @return Set of valid NPC IDs, or null if loading failed
     */
    private static Set<Integer> loadValidNpcIds() {
        File serverRoot = new File(".").getAbsoluteFile();
        File dataFolder = new File(serverRoot, ".scrubians/data");

        if (!dataFolder.exists()) {
            Scrubians.logger("error", "Data folder does not exist: " + dataFolder.getAbsolutePath());
            return null;
        }

        File[] files = dataFolder.listFiles((dir, name) ->
                name.startsWith("scrubians_npcs") && name.endsWith(".json")
        );

        if (files == null || files.length == 0) {
            Scrubians.logger("warn", "No NPC data files found.");
            return new HashSet<>(); // Return empty set - this means all NPCs should be removed
        }

        Set<Integer> validIds = new HashSet<>();
        Gson gson = new Gson();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                JsonArray array = gson.fromJson(reader, JsonArray.class);
                if (array == null) continue;

                for (int i = 0; i < array.size(); i++) {
                    JsonObject npc = array.get(i).getAsJsonObject();
                    if (npc.has("id")) {
                        int npcId = npc.get("id").getAsInt();
                        validIds.add(npcId);
                    }
                }

                Scrubians.logger("info", "Loaded " + array.size() + " NPC(s) from " + file.getName());

            } catch (IOException e) {
                Scrubians.logger("error", "Failed to read NPC data file " + file.getName() + ": " + e.getMessage());
                return null;
            } catch (Exception e) {
                Scrubians.logger("error", "Failed to parse NPC data file " + file.getName() + ": " + e.getMessage());
                return null;
            }
        }

        Scrubians.logger("info", "Total valid NPC IDs loaded: " + validIds.size());
        return validIds;
    }

    /**
     * Programmatically reload NPCs (can be called from other code)
     * @param world The world to reload NPCs in
     * @return Number of NPCs removed
     */
    public static int reloadNpcsInWorld(ServerWorld world) {
        Set<Integer> validNpcIds = loadValidNpcIds();

        if (validNpcIds == null) {
            Scrubians.logger("error", "Failed to reload NPCs: could not load data files");
            return 0;
        }

        int removed = 0;

        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TrackingMannequinEntity trackingNpc) {
                int npcId = trackingNpc.getNpcId();

                if (!validNpcIds.contains(npcId)) {
                    trackingNpc.discard();
                    removed++;
                }
            } else if (entity instanceof MannequinEntity mannequin) {
                mannequin.discard();
                removed++;
            }
        }

        return removed;
    }

    /**
     * Reload NPCs across all worlds in a server
     * @param source The command source (to get the server)
     * @return Total number of NPCs removed
     */
    public static int reloadAllNpcs(ServerCommandSource source) {
        Set<Integer> validNpcIds = loadValidNpcIds();

        if (validNpcIds == null) {
            return 0;
        }

        int totalRemoved = 0;

        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof TrackingMannequinEntity trackingNpc) {
                    int npcId = trackingNpc.getNpcId();

                    if (!validNpcIds.contains(npcId)) {
                        trackingNpc.discard();
                        totalRemoved++;
                    }
                } else if (entity instanceof MannequinEntity mannequin) {
                    mannequin.discard();
                    totalRemoved++;
                }
            }
        }

        return totalRemoved;
    }
}