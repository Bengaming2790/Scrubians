package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.dialogue.DialogueActionHandler;
import ca.techgarage.scrubians.dialogue.DialogueSessionManager;
import ca.techgarage.scrubians.dialogue.NPCDialogue;
import ca.techgarage.scrubians.dialogue.DialoguePackets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DialogueActionTestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("dialogueactiontest")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(DialogueActionTestCommand::testActions)
        );
    }

    private static int testActions(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();

        // Register some custom test actions
        DialogueActionHandler.registerGiveItemAction("give_diamond", new ItemStack(Items.DIAMOND, 1));
        DialogueActionHandler.registerGiveItemAction("give_bread", new ItemStack(Items.BREAD, 5));

        //DialogueActionHandler.registerCommandAction("heal", "effect give @s minecraft:instant_health 1 0");

        // Create test dialogue with various actions
        NPCDialogue dialogue = new NPCDialogue("Action Tester");

        dialogue.addPage("§eWelcome to the action test!");

        dialogue.addPageWithOptions(
                "Pick an option to test different actions:",
                new NPCDialogue.DialogueOption("Test Action (plays sound)", "test"),
                new NPCDialogue.DialogueOption("Get a Diamond", "give_diamond"),
                new NPCDialogue.DialogueOption("Get Bread", "give_bread"),
                new NPCDialogue.DialogueOption("Continue", "next")
        );

        dialogue.addPageWithOptions(
                "More options:",
                new NPCDialogue.DialogueOption("Show About Info", "about"),
                new NPCDialogue.DialogueOption("Show Nearby", "nearby"),
                new NPCDialogue.DialogueOption("Open Trade", "trade"),
                new NPCDialogue.DialogueOption("Get Quest", "quest")
        );

        dialogue.addPage("Actions are working! Each option can trigger custom behavior.");

        dialogue.addPageWithOptions(
                "Want to end the dialogue?",
                new NPCDialogue.DialogueOption("Yes, goodbye!", "bye"),
                new NPCDialogue.DialogueOption("No, restart", "restart")
        );

        // Start dialogue
        DialogueSessionManager.startDialogue(player, -1, dialogue);
        DialoguePackets.sendDialogue(player, dialogue);

        source.sendFeedback(() -> Text.literal("§a=== Action Test Started ==="), false);
        source.sendFeedback(() -> Text.literal("§7Watch chat and listen for sounds as you pick options!"), false);
        source.sendFeedback(() -> Text.literal("§7Use /dialoguechoice <action> to pick options"), false);
        source.sendFeedback(() -> Text.literal("§eAvailable actions: test, give_diamond, give_bread, about, nearby, trade, quest, bye"), false);

        return 1;
    }
}