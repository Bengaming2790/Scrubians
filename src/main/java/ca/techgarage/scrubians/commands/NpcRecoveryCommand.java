package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Files;

public class NpcRecoveryCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("recover").requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.recover"))
                        .then(CommandManager.literal("list")
                                .executes(NpcRecoveryCommand::listBackups)
                        )
                        .then(CommandManager.literal("clean")
                                .executes(NpcRecoveryCommand::cleanStart)
                        ))
        );
    }

    private static int listBackups(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        File serverRoot = new File(".").getAbsoluteFile();
        File scrubiansFolder = new File(serverRoot, ".scrubians");
        File dataFolder = new File(scrubiansFolder, "data");

        if (!dataFolder.exists()) {
            source.sendError(Text.literal("§c.scrubians/data folder not found!"));
            return 0;
        }

        File[] files = dataFolder.listFiles((dir, name) ->
                name.startsWith("scrubians_npcs") && name.endsWith(".json"));

        if (files == null || files.length == 0) {
            source.sendError(Text.literal("§cNo backup files found!"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§e=== NPC Files ==="), false);
        for (File file : files) {
            long sizeKB = file.length() / 1024;
            String name = file.getName();
            String status = name.contains("corrupted") ? "§c[CORRUPTED]" :
                    name.equals("scrubians_npcs.json") ? "§a[ACTIVE]" : "§7[BACKUP]";
            source.sendFeedback(() -> Text.literal(status + " §f" + name + " §7(" + sizeKB + " KB)"), false);
        }

        return 1;
    }

    private static int cleanStart(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        File serverRoot = new File(".").getAbsoluteFile();
        File scrubiansFolder = new File(serverRoot, ".scrubians");
        File dataFolder = new File(scrubiansFolder, "data");
        File jsonFile = new File(dataFolder, "scrubians_npcs.json");

        source.sendFeedback(() -> Text.literal("§e=== Starting Fresh ==="), false);
        source.sendFeedback(() -> Text.literal("§cWARNING: This will delete the current NPC data file!"), false);
        source.sendFeedback(() -> Text.literal("§7Current file will be backed up first."), false);

        try {
            if (jsonFile.exists()) {
                // Create backup
                File backup = new File(dataFolder, "scrubians_npcs_manual_backup_" + System.currentTimeMillis() + ".json");
                Files.copy(jsonFile.toPath(), backup.toPath());
                source.sendFeedback(() -> Text.literal("§aBackup created: " + backup.getName()), false);

                // Delete current file
                jsonFile.delete();
                source.sendFeedback(() -> Text.literal("§aDeleted corrupted file"), false);
            }

            // Reinitialize with empty data
            NpcRegistry.clear();
            source.sendFeedback(() -> Text.literal("§aCreated fresh NPC registry"), false);
            source.sendFeedback(() -> Text.literal("§eYou can now spawn new NPCs!"), false);

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("§cError during cleanup: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}