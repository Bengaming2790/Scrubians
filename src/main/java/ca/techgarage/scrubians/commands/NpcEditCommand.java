package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.PathEditorSession;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class NpcEditCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(
                                CommandManager.literal("edit")
                                        .then(
                                                CommandManager.argument("npcId", IntegerArgumentType.integer(0))

                                                        // /npc edit <npcId> name <newName>
                                                        .then(CommandManager.literal("name")
                                                                .then(CommandManager.argument("newName", StringArgumentType.string())
                                                                        .executes(NpcEditCommand::editName)
                                                                )
                                                        )

                                                        // /npc edit <npcId> skin <newSkin>
                                                        .then(CommandManager.literal("skin")
                                                                .then(CommandManager.argument("newSkin", StringArgumentType.string())
                                                                        .executes(NpcEditCommand::editSkin)
                                                                )
                                                        )

                                                        // /npc edit <npcId> path ...
                                                        .then(CommandManager.literal("path")

                                                                // start
                                                                .then(CommandManager.literal("start")
                                                                        .executes(NpcEditCommand::startPathEdit)
                                                                )

                                                                // add [waitTicks]
                                                                .then(CommandManager.literal("add")
                                                                        .executes(ctx -> addWaypoint(ctx, 40))
                                                                        .then(CommandManager.argument("waitTicks", IntegerArgumentType.integer(0))
                                                                                .executes(ctx ->
                                                                                        addWaypoint(ctx, IntegerArgumentType.getInteger(ctx, "waitTicks"))
                                                                                )
                                                                        )
                                                                )

                                                                // undo
                                                                .then(CommandManager.literal("undo")
                                                                        .executes(NpcEditCommand::undoWaypoint)
                                                                )

                                                                // save
                                                                .then(CommandManager.literal("save")
                                                                        .executes(NpcEditCommand::savePathEdit)
                                                                )

                                                                // cancel
                                                                .then(CommandManager.literal("cancel")
                                                                        .executes(NpcEditCommand::cancelPathEdit)
                                                                )

                                                                // clear
                                                                .then(CommandManager.literal("clear")
                                                                        .executes(NpcEditCommand::clearPath)
                                                                )

                                                                // setwait <ticks>
                                                                .then(CommandManager.literal("setwait")
                                                                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                                                                .executes(NpcEditCommand::setDefaultWait)
                                                                        )
                                                                )
                                                        )
                                        ).requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.edit") // Requires OP level 2)
                                )
                        )
        );
    }


    private static int editName(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        String newName = StringArgumentType.getString(ctx, "newName");
        ServerCommandSource source = ctx.getSource();

        var npc = NpcRegistry.getNpcById(npcId);
        if (npc.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        NpcRegistry.changeName(npcId, newName);
        source.sendFeedback(() -> Text.literal("§aSuccessfully changed NPC name to '" + newName + "'"), true);
        return 1;
    }

    private static int editSkin(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        String newSkin = StringArgumentType.getString(ctx, "newSkin");
        ServerCommandSource source = ctx.getSource();

        var npc = NpcRegistry.getNpcById(npcId);
        if (npc.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        NpcRegistry.changeSkin(npcId, newSkin);
        source.sendFeedback(() -> Text.literal("§aSuccessfully changed NPC skin to '" + newSkin + "'"), true);
        source.sendFeedback(() -> Text.literal("§eNote: You may need to respawn the NPC for skin changes to take effect"), false);
        return 1;
    }

    private static int startPathEdit(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        var npc = NpcRegistry.getNpcById(npcId);
        if (npc.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        PathEditorSession.start(player, npcId);

        source.sendFeedback(() -> Text.literal("§a§l=== Path Editor Started ==="), false);
        source.sendFeedback(() -> Text.literal("§eEditing path for NPC #" + npcId + " (" + npc.get().name + ")"), false);
        source.sendFeedback(() -> Text.literal("§7Commands:"), false);
        source.sendFeedback(() -> Text.literal("§7  /npcedit " + npcId + " path add §f- Add waypoint at current position"), false);
        source.sendFeedback(() -> Text.literal("§7  /npcedit " + npcId + " path add <wait> §f- Add waypoint with custom wait time"), false);
        source.sendFeedback(() -> Text.literal("§7  /npcedit " + npcId + " path undo §f- Remove last waypoint"), false);
        source.sendFeedback(() -> Text.literal("§7  /npcedit " + npcId + " path setwait <ticks> §f- Set default wait time"), false);
        source.sendFeedback(() -> Text.literal("§7  /npcedit " + npcId + " path save §f- Save and finish"), false);
        source.sendFeedback(() -> Text.literal("§7  /npcedit " + npcId + " path cancel §f- Cancel without saving"), false);

        return 1;
    }

    private static int addWaypoint(CommandContext<ServerCommandSource> ctx, int waitTicks) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        var sessionOpt = PathEditorSession.getSession(player);

        if (sessionOpt.isEmpty()) {
            source.sendError(Text.literal("§cNo active path editing session! Use /npcedit " + npcId + " path start first"));
            return 0;
        }

        PathEditorSession session = sessionOpt.get();
        if (session.getNpcId() != npcId) {
            source.sendError(Text.literal("§cYou're editing a different NPC's path!"));
            return 0;
        }

        Vec3d pos = player.getEntityPos();
        session.addWaypoint(pos, waitTicks);

        int count = session.getWaypointCount();
        double waitSeconds = waitTicks / 20.0;
        source.sendFeedback(() -> Text.literal(String.format("§aWaypoint #%d added at (%.1f, %.1f, %.1f) [Wait: %.1fs]",
                count, pos.x, pos.y, pos.z, waitSeconds)), false);

        return 1;
    }

    private static int undoWaypoint(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        var sessionOpt = PathEditorSession.getSession(player);

        if (sessionOpt.isEmpty()) {
            source.sendError(Text.literal("§cNo active path editing session!"));
            return 0;
        }

        PathEditorSession session = sessionOpt.get();
        if (session.getNpcId() != npcId) {
            source.sendError(Text.literal("§cYou're editing a different NPC's path!"));
            return 0;
        }

        if (session.undoLastWaypoint()) {
            source.sendFeedback(() -> Text.literal("§eRemoved last waypoint. Now at " + session.getWaypointCount() + " waypoints"), false);
            return 1;
        } else {
            source.sendError(Text.literal("§cNo waypoints to remove!"));
            return 0;
        }
    }

    private static int savePathEdit(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        var sessionOpt = PathEditorSession.getSession(player);

        if (sessionOpt.isEmpty()) {
            source.sendError(Text.literal("§cNo active path editing session!"));
            return 0;
        }

        PathEditorSession session = sessionOpt.get();
        if (session.getNpcId() != npcId) {
            source.sendError(Text.literal("§cYou're editing a different NPC's path!"));
            return 0;
        }

        int count = session.getWaypointCount();
        session.save();

        source.sendFeedback(() -> Text.literal("§a§lPath saved!"), false);
        source.sendFeedback(() -> Text.literal("§aSaved " + count + " waypoints for NPC #" + npcId), false);
        source.sendFeedback(() -> Text.literal("§eThe NPC will start following this path on next spawn/restart"), false);

        return 1;
    }

    private static int cancelPathEdit(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        var sessionOpt = PathEditorSession.getSession(player);

        if (sessionOpt.isEmpty()) {
            source.sendError(Text.literal("§cNo active path editing session!"));
            return 0;
        }

        PathEditorSession session = sessionOpt.get();
        session.cancel();

        source.sendFeedback(() -> Text.literal("§ePattern editing cancelled. Changes were not saved."), false);
        return 1;
    }

    private static int clearPath(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        var npc = NpcRegistry.getNpcById(npcId);
        if (npc.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found!"));
            return 0;
        }

        NpcRegistry.clearPath(npcId);
        source.sendFeedback(() -> Text.literal("§aCleared path for NPC #" + npcId), true);
        return 1;
    }

    private static int setDefaultWait(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        ServerCommandSource source = ctx.getSource();

        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendError(Text.literal("§cThis command must be run by a player!"));
            return 0;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        var sessionOpt = PathEditorSession.getSession(player);

        if (sessionOpt.isEmpty()) {
            source.sendError(Text.literal("§cNo active path editing session!"));
            return 0;
        }

        PathEditorSession session = sessionOpt.get();
        if (session.getNpcId() != npcId) {
            source.sendError(Text.literal("§cYou're editing a different NPC's path!"));
            return 0;
        }

        session.setDefaultWaitTicks(ticks);
        double seconds = ticks / 20.0;
        source.sendFeedback(() -> Text.literal(String.format("§aDefault wait time set to %d ticks (%.1f seconds)", ticks, seconds)), false);

        return 1;
    }
}