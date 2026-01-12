package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class NpcRemoveAllCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .then(CommandManager.literal("removeall").requires(source -> ScrubiansPermissions.has(source, "scrubians.npc.removeall"))
                        .executes(NpcRemoveAllCommand::removeAll))
        );
    }

    private static int removeAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        // Find all TrackingMannequinEntity instances in the world
        List<TrackingMannequinEntity> npcsToRemove = new ArrayList<>();

        world.iterateEntities().forEach(entity -> {
            if (entity instanceof TrackingMannequinEntity) {
                npcsToRemove.add((TrackingMannequinEntity) entity);
            }
        });

        if (npcsToRemove.isEmpty()) {
            source.sendError(Text.literal("§cNo NPCs found in the world!"));
            return 0;
        }

        int count = npcsToRemove.size();

        // Remove all found NPCs
        for (TrackingMannequinEntity npc : npcsToRemove) {
            npc.discard();
        }

        source.sendFeedback(() -> Text.literal("§aRemoved " + count + " NPC(s) from the world"), true);
        source.sendFeedback(() -> Text.literal("§7Note: This only removes entities, not registry data"), false);
        source.sendFeedback(() -> Text.literal("§7Use /npc respawn all to respawn NPCs from registry"), false);

        return count;
    }


    public static int removeAllServerStart(MinecraftServer server) {

        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla("overworld")));


        // Find all TrackingMannequinEntity instances in the world
        List<TrackingMannequinEntity> npcsToRemove = new ArrayList<>();

        world.iterateEntities().forEach(entity -> {
            if (entity instanceof TrackingMannequinEntity) {
                npcsToRemove.add((TrackingMannequinEntity) entity);
            }
        });

        if (npcsToRemove.isEmpty()) {
            return 0;
        }

        int count = npcsToRemove.size();

        // Remove all found NPCs
        for (TrackingMannequinEntity npc : npcsToRemove) {
            npc.discard();
        }

        return count;
    }

}