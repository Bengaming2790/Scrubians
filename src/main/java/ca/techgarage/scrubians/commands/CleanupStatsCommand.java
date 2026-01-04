package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.events.ChunkLoadCleanup;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicInteger;

public class CleanupStatsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc").then(CommandManager.literal("cleanupstats").requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.cleanup"))
                        .executes(CleanupStatsCommand::showStats))
        );
    }

    private static int showStats(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        source.sendFeedback(() -> Text.literal("§e=== Cleanup Statistics ==="), false);

        // Show total cleaned this session
        int totalCleaned = ChunkLoadCleanup.getTotalCleaned();
        source.sendFeedback(() -> Text.literal("§7Mannequins cleaned this session: §f" + totalCleaned), false);

        // Count current mannequins
        AtomicInteger validNpcs = new AtomicInteger(0);
        AtomicInteger invalidNpcs = new AtomicInteger(0);
        AtomicInteger regularMannequins = new AtomicInteger(0);

        world.iterateEntities().forEach(entity -> {
            if (entity instanceof MannequinEntity mannequin) {
                if (mannequin instanceof TrackingMannequinEntity trackingNpc) {
                    int npcId = trackingNpc.getNpcId();
                    if (npcId >= 0 && NpcRegistry.getNpcById(npcId).isPresent()) {
                        validNpcs.incrementAndGet();
                    } else {
                        invalidNpcs.incrementAndGet();
                    }
                } else {
                    regularMannequins.incrementAndGet();
                }
            }
        });

        source.sendFeedback(() -> Text.literal("§7"), false);
        source.sendFeedback(() -> Text.literal("§7Current Mannequins in Loaded Chunks:"), false);
        source.sendFeedback(() -> Text.literal("  §aValid NPCs: §f" + validNpcs.get()), false);
        source.sendFeedback(() -> Text.literal("  §cInvalid NPCs: §f" + invalidNpcs.get()), false);
        source.sendFeedback(() -> Text.literal("  §7Regular Mannequins: §f" + regularMannequins.get()), false);

        if (invalidNpcs.get() > 0) {
            source.sendFeedback(() -> Text.literal("§e⚠ Invalid NPCs detected in loaded chunks!"), false);
            source.sendFeedback(() -> Text.literal("§7These will be removed when you get close to them"), false);
        } else if (validNpcs.get() > 0) {
            source.sendFeedback(() -> Text.literal("§a✓ All NPCs in loaded chunks are valid!"), false);
        }

        source.sendFeedback(() -> Text.literal("§7"), false);
        source.sendFeedback(() -> Text.literal("§7Note: Invalid mannequins in unloaded chunks"), false);
        source.sendFeedback(() -> Text.literal("§7will be cleaned automatically when those chunks load."), false);

        return 1;
    }
}