package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class NPCDiagnoseCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(
                                CommandManager.literal("diagnose")
                                        .executes(NPCDiagnoseCommand::diagnose)
                        )
        );
    }


    private static int diagnose(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        source.sendFeedback(() -> Text.literal("§e=== NPC Diagnosis ==="), false);

        // Check registry
        var allNpcs = NpcRegistry.getAllNpcs();
        source.sendFeedback(() -> Text.literal("§7NPCs in Registry: §f" + allNpcs.size()), false);

        if (!allNpcs.isEmpty()) {
            for (var npc : allNpcs) {
                int pathSize = npc.getPath() != null ? npc.getPath().size() : 0;
                int dialoguePages = (npc.getDialogue() != null && npc.getDialogue().pages != null)
                        ? npc.getDialogue().pages.size() : 0;

                source.sendFeedback(() -> Text.literal(
                        String.format("  §7#%d: §f%s §7at (%.1f, %.1f, %.1f) [%d waypoints, %d dialogue pages]",
                                npc.id, npc.name, npc.x, npc.y, npc.z, pathSize, dialoguePages)
                ), false);
            }
        }

        // Check entities in world
        List<TrackingMannequinEntity> worldNpcs = new ArrayList<>();
        world.iterateEntities().forEach(entity -> {
            if (entity instanceof TrackingMannequinEntity) {
                worldNpcs.add((TrackingMannequinEntity) entity);
            }
        });

        source.sendFeedback(() -> Text.literal("§7NPCs in World: §f" + worldNpcs.size()), false);

        int validIds = 0;
        int invalidIds = 0;

        for (TrackingMannequinEntity npc : worldNpcs) {
            int id = npc.getNpcId();
            String name = npc.getCustomName() != null ? npc.getCustomName().getString() : "Unknown";
            var pos = npc.getEntityPos();

            if (id >= 0) {
                var registryNpc = NpcRegistry.getNpcById(id);
                if (registryNpc.isPresent()) {
                    validIds++;
                    source.sendFeedback(() -> Text.literal(
                            String.format("  §a✓ %s §7(ID: %d) at (%.1f, %.1f, %.1f)",
                                    name, id, pos.x, pos.y, pos.z)
                    ), false);
                } else {
                    invalidIds++;
                    source.sendFeedback(() -> Text.literal(
                            String.format("  §c✗ %s §7(ID: %d - NOT IN REGISTRY!) at (%.1f, %.1f, %.1f)",
                                    name, id, pos.x, pos.y, pos.z)
                    ), false);
                }
            } else {
                invalidIds++;
                source.sendFeedback(() -> Text.literal(
                        String.format("  §c✗ %s §7(NO ID!) at (%.1f, %.1f, %.1f)",
                                name, pos.x, pos.y, pos.z)
                ), false);
            }
        }

        // Summary
        source.sendFeedback(() -> Text.literal("§e=== Summary ==="), false);
        int finalValidIds = validIds;
        source.sendFeedback(() -> Text.literal("§aValid NPCs: §f" + finalValidIds), false);
        int finalInvalidIds = invalidIds;
        source.sendFeedback(() -> Text.literal("§cInvalid NPCs: §f" + finalInvalidIds), false);

        if (invalidIds > 0) {
            source.sendFeedback(() -> Text.literal("§e⚠ Issues detected!"), false);
            source.sendFeedback(() -> Text.literal("§7Possible causes:"), false);
            source.sendFeedback(() -> Text.literal("§7  1. NPCs spawned before registry was loaded"), false);
            source.sendFeedback(() -> Text.literal("§7  2. NBT data not saving properly"), false);
            source.sendFeedback(() -> Text.literal("§7  3. Registry file was deleted/reset"), false);
            source.sendFeedback(() -> Text.literal("§7Fix: Use /npcremoveall then /npcrespawn all"), false);
        } else if (validIds > 0) {
            source.sendFeedback(() -> Text.literal("§a✓ All NPCs are properly configured!"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§7No NPCs found in world"), false);
        }

        return 1;
    }
}