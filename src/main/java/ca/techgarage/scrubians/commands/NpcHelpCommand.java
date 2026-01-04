package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class NpcHelpCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("help")
                                .requires(source ->
                                        ScrubiansPermissions.has(source, "scrubians.npc")
                                )
                                .executes(context -> execute(context.getSource()))
                        )
        );
    }

    private static int execute(ServerCommandSource source) {
         source.sendMessage(Text.literal("§6[ Scrubians NPC Help ]"));
         source.sendMessage(Text.literal("§e/npc help §7- Show this help message"));
            source.sendMessage(Text.literal("§e/npc list §7- List all registered NPCs"));
            source.sendMessage(Text.literal("§e/npc edit <id> {skin, name, path} §7- Edit dialogue for NPC with specified ID With various subcommands"));
            source.sendMessage(Text.literal("§e/npc diagnose §7- Diagnose NPC registry and entities"));
            source.sendMessage(Text.literal("§e/npc killInvalid §7- Remove all invalid NPC entities from the world"));
            source.sendMessage(Text.literal("§e/npc removeall §7- Remove all NPC entities from the world"));
            source.sendMessage(Text.literal("§e/npc respawn all §7- Respawn all NPCs from the registry"));
            source.sendMessage(Text.literal("§e/npc respawn <id> §7- Respawn NPC with specified ID from the registry"));
            source.sendMessage(Text.literal("§e/npc create {skin, name, path} §7- Create a new NPC at your current location with various options"));
            source.sendMessage(Text.literal("§e/npc dialogue <id> §7- Open dialogue editor for NPC with specified ID"));

        return 1;
    }
}
