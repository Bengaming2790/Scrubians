package ca.techgarage.scrubians.dialogue;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages active dialogue sessions for players
 */
public class DialogueSessionManager {

    private static final Map<UUID, DialogueSession> ACTIVE_SESSIONS = new HashMap<>();

    public static class DialogueSession {
        private final NPCDialogue dialogue;
        private final int npcId;

        public DialogueSession(NPCDialogue dialogue, int npcId) {
            this.dialogue = dialogue;
            this.npcId = npcId;
        }

        public NPCDialogue getDialogue() {
            return dialogue;
        }

        public int getNpcId() {
            return npcId;
        }

        /**
         * Check if an action ID is valid for the current page
         */
        public boolean isValidActionForCurrentPage(String actionId) {
            NPCDialogue.DialoguePage page = dialogue.getCurrentPageData();
            if (page == null) return false;

            // "close" is always valid
            if (actionId.equalsIgnoreCase("close")) {
                return true;
            }

            //Checks if the string is one of the options on the page
            for (NPCDialogue.DialogueOption option : page.getOptions()) {
                if (option.getActionId().equals(actionId)) {
                    return true; // Found matching option on current page
                }
            }

            return false;
        }
    }

    /**
     * Start a new dialogue session
     * @return true if dialogue was started, false if player already has an active dialogue
     */
    public static boolean startDialogue(ServerPlayerEntity player, int npcId, NPCDialogue dialogue) {
        // Check if player already has an active dialogue
        if (hasActiveDialogue(player)) {
            player.sendMessage(net.minecraft.text.Text.literal("§cYou are already in a dialogue!"), false);
            return false;
        }

        dialogue.reset();
        ACTIVE_SESSIONS.put(player.getUuid(), new DialogueSession(dialogue, npcId));
        DialoguePackets.sendDialogue(player, dialogue);
        return true;
    }

    /**
     * Get the active dialogue session for a player
     */
    public static DialogueSession getSession(ServerPlayerEntity player) {
        return ACTIVE_SESSIONS.get(player.getUuid());
    }

    /**
     * Check if player has an active dialogue
     */
    public static boolean hasActiveDialogue(ServerPlayerEntity player) {
        return ACTIVE_SESSIONS.containsKey(player.getUuid());
    }

    /**
     * End the dialogue session
     */
    public static void endDialogue(ServerPlayerEntity player) {
        ACTIVE_SESSIONS.remove(player.getUuid());
        player.sendMessage(net.minecraft.text.Text.literal("§8[Dialogue ended]"), false);
    }

    /**
     * Advance to next page
     */
    public static boolean nextPage(ServerPlayerEntity player) {
        DialogueSession session = getSession(player);
        if (session != null) {
            NPCDialogue dialogue = session.getDialogue();
            if (dialogue.hasNextPage()) {
                dialogue.nextPage();
                DialoguePackets.sendDialogue(player, dialogue);
                return true;
            } else {
                // End of dialogue
                endDialogue(player);
                return false;
            }
        }
        return false;
    }

    /**
     * Jump to a specific page (1-indexed from action ID)
     */
    public static boolean goToPage(ServerPlayerEntity player, int pageNumber) {
        DialogueSession session = getSession(player);
        if (session != null) {
            NPCDialogue dialogue = session.getDialogue();
            // pageNumber from action is 1-indexed, convert to 0-indexed
            int targetPage = pageNumber - 1;

            if (targetPage >= 0 && targetPage < dialogue.getPages().size()) {
                dialogue.setCurrentPage(targetPage);
                DialoguePackets.sendDialogue(player, dialogue);
                return true;
            }
        }
        return false;
    }

    /**
     * Handle option click from chat
     * SECURITY: Only actions present in the current dialogue page can be executed
     */
    public static void handleOptionClick(ServerPlayerEntity player, String actionId) {
        DialogueSession session = getSession(player);
        if (session == null) return;

       // System.out.println("[Scrubians] Player " + player.getName().getString() + " clicked option: " + actionId);

        // SECURITY: Validate that the action is valid for the current page
        // This prevents players from typing /dialogueaction run_op_@s or similar exploits
        if (!session.isValidActionForCurrentPage(actionId)) {
            player.sendMessage(net.minecraft.text.Text.literal("§cInvalid dialogue action!"), false);
            System.out.println("[Scrubians]: SECURITY WARNING: Player " + player.getName().getString() +
                    " attempted invalid action: " + actionId);
            endDialogue(player);
            return;
        }

        // Handle "close" action
        if (actionId.equalsIgnoreCase("close")) {
            endDialogue(player);
            return;
        }

        // Handle "next" action
        if (actionId.equalsIgnoreCase("next")) {
            nextPage(player);
            return;
        }

        // Check if action is a page number (integer)
        try {
            int pageNumber = Integer.parseInt(actionId);
            if (!goToPage(player, pageNumber)) {
                player.sendMessage(net.minecraft.text.Text.literal("§cInvalid page number: " + pageNumber), false);
                endDialogue(player);
            }
            return;
        } catch (NumberFormatException e) {
            // Not a number, continue to command/custom action handling
        }

        // Handle run_command actions
        if (actionId.startsWith("run_")) {
            String command = actionId.substring(4).replace("_", " ");
            System.out.println("[Scrubians] Executing command: /" + command);

            // Execute command as server with the player's command source
            player.getEntityWorld().getServer().getCommandManager().parseAndExecute(
                    player.getCommandSource(),
                    command
            );

            // Keep dialogue open after running command
            DialoguePackets.sendDialogue(player, session.getDialogue());
            return;
        }

        // Handle custom actions via DialogueActionHandler
        boolean shouldContinue = DialogueActionHandler.handleAction(player, session.getNpcId(), actionId);

        if (!shouldContinue) {
            endDialogue(player);
        } else {
            // Action handled, refresh dialogue display
            DialoguePackets.sendDialogue(player, session.getDialogue());
        }
    }
}