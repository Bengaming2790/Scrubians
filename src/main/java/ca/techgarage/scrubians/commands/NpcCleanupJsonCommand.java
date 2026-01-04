package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.*;

public class NpcCleanupJsonCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")

                        .then(
                                CommandManager.literal("cleanupjson")
                                        .executes(NpcCleanupJsonCommand::cleanup)
                        ).requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.cleanupjson")) // Requires OP level 2
        );
    }


    private static int cleanup(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        source.sendFeedback(() -> Text.literal("§e=== JSON Cleanup ==="), false);

        // Get all NPCs from registry
        var allNpcs = NpcRegistry.getAllNpcs();
        source.sendFeedback(() -> Text.literal("§7Registry contains: §f" + allNpcs.size() + " entries"), false);

        // Get all NPCs actually in the world
        Map<Integer, TrackingMannequinEntity> worldNpcs = new HashMap<>();
        world.iterateEntities().forEach(entity -> {
            if (entity instanceof TrackingMannequinEntity npc) {
                int id = npc.getNpcId();
                if (id >= 0) {
                    worldNpcs.put(id, npc);
                }
            }
        });

        source.sendFeedback(() -> Text.literal("§7World contains: §f" + worldNpcs.size() + " valid NPCs"), false);

        // Find orphaned registry entries (in JSON but not in world)
        List<Integer> orphanedIds = new ArrayList<>();
        for (var npc : allNpcs) {
            if (!worldNpcs.containsKey(npc.id)) {
                orphanedIds.add(npc.id);
            }
        }

        if (orphanedIds.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§a✓ No orphaned entries found!"), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§eFound " + orphanedIds.size() + " orphaned entries:"), false);

        for (int id : orphanedIds) {
            var npcOpt = NpcRegistry.getNpcById(id);
            if (npcOpt.isPresent()) {
                var npc = npcOpt.get();
                source.sendFeedback(() -> Text.literal(
                        String.format("  §c✗ ID %d: %s at (%.1f, %.1f, %.1f)",
                                npc.id, npc.name, npc.x, npc.y, npc.z)
                ), false);
            }
        }

        source.sendFeedback(() -> Text.literal("§7Removing orphaned entries..."), false);

        // Remove orphaned entries
        for (int id : orphanedIds) {
            NpcRegistry.removeNpcById(id);
        }

        source.sendFeedback(() -> Text.literal("§a✓ Removed " + orphanedIds.size() + " orphaned entries!"), true);
        source.sendFeedback(() -> Text.literal("§7Registry now contains: §f" + NpcRegistry.getAllNpcs().size() + " entries"), false);

        return 1;
    }
}