package ca.techgarage.scrubians.dialogue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Command to handle dialogue actions from clickable chat messages
 */
public class DialogueActionCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("dialogueaction").requires(Permissions.require("scrubians.dialogue")).then(CommandManager.argument("action", StringArgumentType.greedyString()).executes(DialogueActionCommand::handleAction)));
    }

    private static int handleAction(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String actionId = "";

            actionId = StringArgumentType.getString(ctx, "action");

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();

        if (!DialogueSessionManager.hasActiveDialogue(player)) {
            source.sendError(Text.literal("§cYou don't have an active dialogue!"));
            return 0;
        }

        DialogueSessionManager.handleOptionClick(player, actionId);
        return 1;
    }
}