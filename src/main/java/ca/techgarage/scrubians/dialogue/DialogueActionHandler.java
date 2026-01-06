package ca.techgarage.scrubians.dialogue;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import ca.techgarage.scrubians.Scrubians;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Handles custom actions triggered by dialogue options
 * Note: "close" and page numbers are handled by DialogueSessionManager
 */
public class DialogueActionHandler {

    private static final Map<String, BiConsumer<ServerPlayerEntity, Integer>> ACTION_HANDLERS = new HashMap<>();

    static {
        // Register example custom actions
        registerAction("test", DialogueActionHandler::handleTest);
        registerAction("bye", DialogueActionHandler::handleGoodbye);
        registerAction("trade", DialogueActionHandler::handleTrade);
        registerAction("quest", DialogueActionHandler::handleQuest);
        registerAction("help", DialogueActionHandler::handleHelp);

        // Add more custom actions here as needed
    }

    /**
     * Register a custom action handler
     */
    public static void registerAction(String actionId, BiConsumer<ServerPlayerEntity, Integer> handler) {
        ACTION_HANDLERS.put(actionId, handler);
        Scrubians.logger("info", "[Scrubians] Registered dialogue action: " + actionId);
    }

    /**
     * Handle an action triggered by dialogue option
     * @return true if dialogue should continue, false if it should end
     */
    public static boolean handleAction(ServerPlayerEntity player, int npcId, String actionId) {
        Scrubians.logger("info", "[Scrubians] Player " + player.getName().getString() + " triggered action: " + actionId);

        BiConsumer<ServerPlayerEntity, Integer> handler = ACTION_HANDLERS.get(actionId);

        if (handler != null) {
            handler.accept(player, npcId);

            // Special actions that end dialogue
            if (actionId.equals("bye")) {
                return false;
            }

            return true;
        } else {
            // Unknown action - just log it and continue
            player.sendMessage(Text.literal("§7[Action: " + actionId + " - Not implemented yet]"), false);
            return true;
        }
    }

    // ===== Example Action Handlers =====

    private static void handleTest(ServerPlayerEntity player, int npcId) {
        player.sendMessage(Text.literal("§a✓ Test action works!"), false);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    private static void handleGoodbye(ServerPlayerEntity player, int npcId) {
        player.sendMessage(Text.literal("§e*The NPC waves goodbye*"), false);
        player.playSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private static void handleTrade(ServerPlayerEntity player, int npcId) {
        player.sendMessage(Text.literal("§7[Trade system not implemented yet]"), false);
        player.playSound(SoundEvents.BLOCK_CHEST_OPEN);
    }

    private static void handleQuest(ServerPlayerEntity player, int npcId) {
        player.sendMessage(Text.literal("§7[Quest system not implemented yet]"), false);
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
    }

    private static void handleHelp(ServerPlayerEntity player, int npcId) {
        player.sendMessage(Text.literal("§7[Help system not implemented yet]"), false);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value());
    }

    // ===== Example: More Complex Actions =====

    /**
     * Example: Give player an item
     */
    public static void registerGiveItemAction(String actionId, net.minecraft.item.ItemStack item) {
        registerAction(actionId, (player, npcId) -> {
            player.getInventory().insertStack(item.copy());
            player.sendMessage(Text.literal("§aReceived: " + item.getName().getString()), false);
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP);
        });
    }

}
