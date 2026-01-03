package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.dialogue.DialogueSessionManager;
import ca.techgarage.scrubians.dialogue.NPCDialogue;
import ca.techgarage.scrubians.dialogue.DialoguePackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DialogueTestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("dialoguetest")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(DialogueTestCommand::testDialogue)
        );
    }

    private static int testDialogue(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();

        // Create test dialogue
        NPCDialogue dialogue = new NPCDialogue("Test NPC");

        dialogue.addPage("§eWelcome!§r This is a test of the dialogue system.");

        dialogue.addPageWithOptions(
                "What would you like to do?",
                new NPCDialogue.DialogueOption("Learn More", "learn"),
                new NPCDialogue.DialogueOption("Trade", "trade"),
                new NPCDialogue.DialogueOption("Leave", "leave")
        );

        dialogue.addPage("This dialogue system supports multiple pages and clickable options!");

        dialogue.addPage("That's all for now. Thanks for testing!");

        // Start dialogue
        DialogueSessionManager.startDialogue(player, -1, dialogue);
        DialoguePackets.sendDialogue(player, dialogue);

        source.sendFeedback(() -> Text.literal("§aDialogue test started! Right-click NPCs to trigger their dialogue."), false);

        return 1;
    }
}