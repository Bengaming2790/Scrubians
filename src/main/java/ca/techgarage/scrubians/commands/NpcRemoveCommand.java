package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.Scrubians;
import ca.techgarage.scrubians.ScrubiansPermissions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class NpcRemoveCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("delete")
                                .requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.delete"))
                                .then(CommandManager.argument("npcId", IntegerArgumentType.integer(0))
                                        .executes(NpcRemoveCommand::execute)))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");

        File serverRoot = new File(".").getAbsoluteFile();
        File dataFolder = new File(serverRoot, ".scrubians/data");

        File[] files = dataFolder.listFiles((dir, name) ->
                name.startsWith("scrubians_npcs") && name.endsWith(".json")
        );

        if (files == null || files.length == 0) {
            Scrubians.logger("error", "No NPC data files found.");
            source.sendError(Text.literal("No NPC data files found."));
            return 0;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        boolean deleted = false;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                JsonArray array = gson.fromJson(reader, JsonArray.class);
                if (array == null) continue;

                for (int i = 0; i < array.size(); i++) {
                    JsonObject npc = array.get(i).getAsJsonObject();
                    if (npc.has("id") && npc.get("id").getAsInt() == npcId) {
                        array.remove(i);
                        deleted = true;
                        break;
                    }
                }

                if (deleted) {
                    try (FileWriter writer = new FileWriter(file)) {
                        gson.toJson(array, writer);
                    }
                    break;
                }

            } catch (IOException e) {
                Scrubians.logger("error", "Failed to delete NPC #" + npcId + ": " + e.getMessage());
                source.sendError(Text.literal("Failed to delete NPC #" + npcId + ": " + e.getMessage()));
                return 0;
            }
        }

        if (!deleted) {
            source.sendError(Text.literal("NPC with id " + npcId + " not found."));
            return 0;
        }

        source.sendFeedback(() ->
                Text.literal("Deleted NPC with id " + npcId), true
        );
        source.sendFeedback(() ->
                        Text.literal("Please use the /npc reload command to initiate changes"), true
                );

        return 1;
    }
}