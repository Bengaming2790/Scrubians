package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.npcs.NpcEntityFactory;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class SpawnNpcCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .requires(source -> source.hasPermissionLevel(2)) // Requires OP level 2
                        .then(CommandManager.literal("create")

                            // Just name (uses default Steve skin, not attackable)
                            .then(CommandManager.argument("name", StringArgumentType.string())
                                    .executes(context -> execute(context, null, false))
                                    // Name + skinName
                                    .then(CommandManager.argument("skinName", StringArgumentType.string())
                                            .executes(context -> execute(context, StringArgumentType.getString(context, "skinName"), false))
                                            // Name + skinName + attackable
                                            .then(CommandManager.argument("attackable", BoolArgumentType.bool())
                                                    .executes(context -> execute(context, StringArgumentType.getString(context, "skinName"), BoolArgumentType.getBool(context, "attackable")))
                                            )
                                    )
                            )
                        )
        );
    }

    private static int execute(CommandContext<ServerCommandSource> context, String skinName, boolean attackable) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        // Handle default skin: if skinName is null or ".", use default
        String actualSkinName = (skinName == null || skinName.equals(".")) ? null : skinName;

        try {
            // Get the world and position
            ServerWorld world = source.getWorld();
            Vec3d pos = source.getPosition();
            // Create the NPC asynchronously (will automatically look at nearby players)
            NpcEntityFactory.createPlayerNpc(world, pos, name, actualSkinName, attackable)
                    .thenAccept(npc -> {
                        // Send success message
                        String skinInfo = actualSkinName == null ? "default Steve skin" : "skin '" + actualSkinName + "'";
                        source.sendFeedback(
                                () -> Text.literal("§aSpawned NPC '§e" + name + "§a' with " + skinInfo + " at your location!"),
                                true
                        );
                    })
                    .exceptionally(throwable -> {
                        source.sendError(Text.literal("§cFailed to spawn NPC: " + throwable.getMessage()));
                        throwable.printStackTrace();
                        return null;
                    });

            return 1; // Success
        } catch (Exception e) {
            source.sendError(Text.literal("§cFailed to spawn NPC: " + e.getMessage()));
            e.printStackTrace();
            return 0; // Failure
        }
    }
}