package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.NpcEntityFactory;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class NpcRespawnCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("respawn")
                        .requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.respawn")) // Requires OP level 2
                        // /npcrespawn <npcId>
                        .then(CommandManager.argument("npcId", IntegerArgumentType.integer(0))
                                .executes(NpcRespawnCommand::respawnSingle)
                        )
                        // /npcrespawn all
                        .then(CommandManager.literal("all")
                                .executes(NpcRespawnCommand::respawnAll)
                        )
                        )
        );
    }

    private static int respawnSingle(CommandContext<ServerCommandSource> ctx) {
        int npcId = IntegerArgumentType.getInteger(ctx, "npcId");
        ServerCommandSource source = ctx.getSource();

        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isEmpty()) {
            source.sendError(Text.literal("§cNPC with ID " + npcId + " not found in registry!"));
            return 0;
        }

        ServerWorld world = source.getWorld();
        NpcRegistry.NpcData npc = npcOpt.get();

        source.sendFeedback(() -> Text.literal("§eRespawning NPC #" + npcId + " (" + npc.name + ")..."), true);

        NpcEntityFactory.respawnNpcFromRegistry(world, npcId).thenAccept(entity -> {
            int pathSize = npc.getPath().size();
            if (pathSize > 0) {
                source.sendFeedback(() -> Text.literal("§aSuccessfully respawned NPC with " + pathSize + " waypoint(s)!"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§aSuccessfully respawned NPC (no path assigned)"), false);
            }
        }).exceptionally(ex -> {
            source.sendError(Text.literal("§cFailed to respawn NPC: " + ex.getMessage()));
            return null;
        });

        return 1;
    }

    private static int respawnAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        var allNpcs = NpcRegistry.getAllNpcs();
        if (allNpcs.isEmpty()) {
            source.sendError(Text.literal("§cNo NPCs found in registry!"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§eRespawning " + allNpcs.size() + " NPC(s)..."), true);

        int[] successCount = {0};
        for (NpcRegistry.NpcData npc : allNpcs) {
            NpcEntityFactory.respawnNpcFromRegistry(world, npc.id).thenAccept(entity -> {
                successCount[0]++;
                if (successCount[0] == allNpcs.size()) {
                    source.sendFeedback(() -> Text.literal("§aSuccessfully respawned all " + successCount[0] + " NPC(s)!"), false);
                }
            }).exceptionally(ex -> {
                source.sendError(Text.literal("§cFailed to respawn NPC #" + npc.id + ": " + ex.getMessage()));
                return null;
            });
        }

        return 1;
    }

    /**
     * Check for NPCs on server start
     * This does NOT respawn - just logs information
     */
    public static void respawnAllOnServerStart(ServerWorld world) {

        var allNpcs = NpcRegistry.getAllNpcs();
        int[] successCount = {0};
        for (NpcRegistry.NpcData npc : allNpcs) {
            NpcEntityFactory.respawnNpcFromRegistry(world, npc.id).thenAccept(entity -> {
                successCount[0]++;

            });
        }

    }
}