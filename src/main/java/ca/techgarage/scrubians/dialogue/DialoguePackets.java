package ca.techgarage.scrubians.dialogue;

import ca.techgarage.scrubians.dialogue.NPCDialogue.DialogueOption;
import ca.techgarage.scrubians.dialogue.NPCDialogue.DialoguePage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Sends dialogue to players via clickable chat messages
 */
public class DialoguePackets {

    /**
     * Send dialogue to player as clickable chat messages
     */
    public static void sendDialogue(ServerPlayerEntity player, NPCDialogue dialogue) {
        DialoguePage page = dialogue.getCurrentPageData();
        if (page == null) return;

        // Send NPC message
        player.sendMessage(Text.literal("§e[" + dialogue.getNpcName() + "]§r " + page.getText()), false);

        // Send clickable options
        if (page.hasOptions()) {
            for (DialogueOption option : page.getOptions()) {
                String actionId = option.getActionId();

                // Create clickable option text
                MutableText optionText = Text.literal("  §a»§b " + option.getText())
                        .styled(style -> style
                                .withClickEvent(new ClickEvent.RunCommand(
                                        "/dialogueaction " + actionId
                                ))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Text.literal("§7Click to select: §f" + option.getText())
                                ))
                        );

                player.sendMessage(optionText, false);
            }
        } else if (dialogue.hasNextPage()) {
            // No options, but has next page - show "Continue" button
            MutableText continueText = Text.literal("  §a»§b Continue")
                    .styled(style -> style
                            .withClickEvent(new ClickEvent.RunCommand(
                                    "/dialogueaction next"
                            ))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Text.literal("§7Click to continue")
                            ))
                    );

            player.sendMessage(continueText, false);
        } else {
            // Last page with no options - show close button
            MutableText closeText = Text.literal("  §c»§7 Close")
                    .styled(style -> style
                            .withClickEvent(new ClickEvent.RunCommand(
                                    "/dialogueaction close"
                            ))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Text.literal("§7Click to close dialogue")
                            ))
                    );

            player.sendMessage(closeText, false);
        }
    }
}