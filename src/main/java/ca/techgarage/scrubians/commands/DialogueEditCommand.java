package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.NpcRegistry.DialogueData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class DialogueEditCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("edit")
                                .requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.edit"))
                                .then(CommandManager.argument("npcId", IntegerArgumentType.integer(0))
                                        .then(CommandManager.literal("dialogue")
                                                // /npc edit <id> dialogue
                                                .executes(DialogueEditCommand::help)

                                                // /npc edit <id> dialogue addpage <text>
                                                .then(CommandManager.literal("addpage")
                                                        .then(CommandManager.argument("text", StringArgumentType.greedyString())
                                                                .executes(DialogueEditCommand::addPage)
                                                        )
                                                )

                                                // /npc edit <id> dialogue addoption <text> <action>
                                                .then(CommandManager.literal("addoption")
                                                        .then(CommandManager.argument("text", StringArgumentType.string())
                                                                .then(CommandManager.argument("action", StringArgumentType.word())
                                                                        .executes(DialogueEditCommand::addOption)
                                                                )
                                                        )
                                                )

                                                // /npc edit <id> dialogue clear
                                                .then(CommandManager.literal("clear")
                                                        .executes(DialogueEditCommand::clearDialogue)
                                                )

                                                // /npc edit <id> dialogue view
                                                .then(CommandManager.literal("view")
                                                        .executes(DialogueEditCommand::viewDialogue)
                                                )
                                        )
                                )
                        )
        );
    }


    private static int help(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        var npc = NpcRegistry.getNpcById(npcId);

        source.sendFeedback(() -> Text.literal("§a§l=== Dialogue Commands ==="), false);
        source.sendFeedback(() -> Text.literal("§eEditing Dialogue for NPC #" + npcId + " (" + npc.get().name + ")"), false);
        source.sendFeedback(() -> Text.literal("§7Commands:"), false);
        source.sendFeedback(() -> Text.literal("§7  /npc edit " + npcId + " dialogue addpage <text> §f- Adds a new page with the text given as NPC dialogue"), false);
        source.sendFeedback(() -> Text.literal("§7  /npc edit " + npcId + " dialogue addoption <text> <actionID> §f- Adds an option to current page with the action id being what is done"), false);
        source.sendFeedback(() -> Text.literal("§fAction IDs: next, close, <pagenumber>, run_<command>"), false);
        source.sendFeedback(() -> Text.literal("§7  /npc edit " + npcId + " dialogue clear §f- clears all dialogue from the NPC"), false);
        source.sendFeedback(() -> Text.literal("§7  /npc edit " + npcId + " dialogue view §f- Lets you view the NPC"), false);
        source.sendFeedback(() -> Text.literal("§7 You may always edit the dialogue in the JSON file!"), false);

        return 1;
    }
    
    
    private static int addPage(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        String text = StringArgumentType.getString(ctx, "text");
        ServerCommandSource source = ctx.getSource();

        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        var npc = npcOpt.get();

        // Get or create dialogue
        DialogueData dialogue = npc.getDialogue();
        if (dialogue == null) {
            dialogue = new DialogueData();
        }

        // Add new page
        DialogueData.DialoguePageData page = new DialogueData.DialoguePageData(text);
        dialogue.pages.add(page);

        // Save
        NpcRegistry.setDialogue(npcId, dialogue);

        int pageNum = dialogue.pages.size();
        source.sendFeedback(() -> Text.literal("§aAdded page #" + pageNum + " to NPC #" + npcId), true);
        source.sendFeedback(() -> Text.literal("§7Text: §f" + text), false);

        return 1;
    }

    private static int addOption(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        String text = StringArgumentType.getString(ctx, "text");
        String action = StringArgumentType.getString(ctx, "action");
        ServerCommandSource source = ctx.getSource();

        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        var npc = npcOpt.get();
        DialogueData dialogue = npc.getDialogue();

        if (dialogue == null || dialogue.pages.isEmpty()) {
            source.sendError(Text.literal("§cNo pages exist! Use /npc dialogue " + npcId + " addpage <text> first"));
            return 0;
        }

        // Add option to last page
        DialogueData.DialoguePageData lastPage = dialogue.pages.get(dialogue.pages.size() - 1);
        DialogueData.DialogueOptionData option = new DialogueData.DialogueOptionData(text, action);
        lastPage.options.add(option);

        // Save
        NpcRegistry.setDialogue(npcId, dialogue);

        source.sendFeedback(() -> Text.literal("§aAdded option to page #" + dialogue.pages.size()), true);
        source.sendFeedback(() -> Text.literal("§7Option: §f" + text + " §7(action: " + action + ")"), false);

        return 1;
    }

    private static int clearDialogue(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        NpcRegistry.setDialogue(npcId, null);
        source.sendFeedback(() -> Text.literal("§aCleared dialogue for NPC #" + npcId), true);

        return 1;
    }

    private static int viewDialogue(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        var npc = npcOpt.get();
        DialogueData dialogue = npc.getDialogue();

        if (dialogue == null || dialogue.pages.isEmpty()) {
            source.sendError(Text.literal("§cNo dialogue set for NPC #" + npcId));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§e=== Dialogue for NPC #" + npcId + " (" + npc.name + ") ==="), false);

        for (int i = 0; i < dialogue.pages.size(); i++) {
            DialogueData.DialoguePageData page = dialogue.pages.get(i);
            int pageNum = i + 1;

            source.sendFeedback(() -> Text.literal("§7Page " + pageNum + ": §f" + page.text), false);

            if (page.options != null && !page.options.isEmpty()) {
                for (DialogueData.DialogueOptionData option : page.options) {
                    source.sendFeedback(() -> Text.literal("  §a▸ §f" + option.text + " §7[" + option.action + "]"), false);
                }
            }
        }

        return 1;
    }
}
