package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.npcs.NpcRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.File;

public class DebugFileLocationCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .requires(source -> source.hasPermissionLevel(2)).then(CommandManager.literal("debug")
                        .executes(DebugFileLocationCommand::debug))
        );
    }

    private static int debug(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        // Use the server's run directory (JAR location)
        File serverRoot = source.getServer().getRunDirectory().toFile();
        String serverPath = serverRoot.getAbsolutePath();

        // Get .scrubians folder path
        File scrubiansFolder = new File(serverRoot, ".scrubians");
        String folderPath = scrubiansFolder.getAbsolutePath();
        boolean folderExists = scrubiansFolder.exists();

        // Get data folder path
        File dataFolder = new File(scrubiansFolder, "data");
        String dataPath = dataFolder.getAbsolutePath();
        boolean dataExists = dataFolder.exists();

        // Get JSON file path
        File jsonFile = new File(dataFolder, "scrubians_npcs.json");
        String jsonPath = jsonFile.getAbsolutePath();
        boolean fileExists = jsonFile.exists();

        // Get registry info
        var allNpcs = NpcRegistry.getAllNpcs();
        int npcCount = allNpcs.size();

        // Send debug info
        source.sendFeedback(() -> Text.literal("§e=== NPC File Debug Info ==="), false);
        source.sendFeedback(() -> Text.literal("§7Server Root: §f" + serverPath), false);
        source.sendFeedback(() -> Text.literal("§7.scrubians Folder: §f" + folderPath), false);
        source.sendFeedback(() -> Text.literal("§7Folder Exists: " + (folderExists ? "§aYES" : "§cNO")), false);
        source.sendFeedback(() -> Text.literal("§7data Folder: §f" + dataPath), false);
        source.sendFeedback(() -> Text.literal("§7data Exists: " + (dataExists ? "§aYES" : "§cNO")), false);
        source.sendFeedback(() -> Text.literal("§7JSON File: §f" + jsonPath), false);
        source.sendFeedback(() -> Text.literal("§7File Exists: " + (fileExists ? "§aYES" : "§cNO")), false);
        source.sendFeedback(() -> Text.literal("§7NPCs in Registry: §f" + npcCount), false);

        if (fileExists) {
            long fileSize = jsonFile.length();
            source.sendFeedback(() -> Text.literal("§7File Size: §f" + fileSize + " bytes"), false);
        }

        // List all NPCs
        if (npcCount > 0) {
            source.sendFeedback(() -> Text.literal("§e=== NPCs in Registry ==="), false);
            for (var npc : allNpcs) {
                int pathSize = npc.getPath() != null ? npc.getPath().size() : 0;
                source.sendFeedback(() -> Text.literal(
                        String.format("§7#%d: §f%s §7(%.1f, %.1f, %.1f) §7[%d waypoints]",
                                npc.id, npc.name, npc.x, npc.y, npc.z, pathSize)
                ), false);
            }
        }

        return 1;
    }
}