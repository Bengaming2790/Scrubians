package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.npcs.SelectionManager;
import ca.techgarage.scrubians.npcs.ViolentNpcRegistry;
import ca.techgarage.scrubians.npcs.ViolentNpcTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Command to create and manage violent NPCs with WorldEdit-style selection
 *
 * Selection Commands:
 * /npc violent pos1 - Set first position of spawn area at player location
 * /npc violent pos2 - Set second position of spawn area at player location
 * /npc violent selection - View current selection
 * /npc violent clearsel - Clear current selection
 *
 * NPC Management:
 * /npc violent create <name> <entityType> - Create new violent NPC with current selection
 * /npc violent setarea <id> - Update spawn area for NPC using current selection
 * /npc violent setstats <id> <health> <damage> <speed> - Set NPC stats
 * /npc violent setcount <id> <maxCount> - Set max spawn count
 * /npc violent setrespawn <id> <seconds> - Set respawn delay
 * /npc violent spawn <id> - Force spawn the NPC
 * /npc violent despawn <id> - Despawn all entities for NPC
 * /npc violent list - List all violent NPCs
 * /npc violent remove <id> - Remove a violent NPC
 */
public class SpawnViolentNpcCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("violent")

                                // WorldEdit-style selection commands
                                .then(CommandManager.literal("pos1")
                                        .executes(SpawnViolentNpcCommand::setPos1)
                                )

                                .then(CommandManager.literal("pos2")
                                        .executes(SpawnViolentNpcCommand::setPos2)
                                )

                                .then(CommandManager.literal("selection")
                                        .executes(SpawnViolentNpcCommand::showSelection)
                                )

                                .then(CommandManager.literal("clearsel")
                                        .executes(SpawnViolentNpcCommand::clearSelection)
                                )

                                // Create new violent NPC using selection
                                .then(CommandManager.literal("create")
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .then(CommandManager.argument("entityType", StringArgumentType.string())
                                                        .executes(SpawnViolentNpcCommand::create)
                                                )
                                        )
                                )

                                // Update spawn area using selection
                                .then(CommandManager.literal("setarea")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .executes(SpawnViolentNpcCommand::setArea)
                                        )
                                )

                                // Set stats
                                .then(CommandManager.literal("setstats")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("health", DoubleArgumentType.doubleArg(1))
                                                        .then(CommandManager.argument("damage", DoubleArgumentType.doubleArg(0))
                                                                .then(CommandManager.argument("speed", DoubleArgumentType.doubleArg(0.1))
                                                                        .executes(SpawnViolentNpcCommand::setStats)
                                                                )
                                                        )
                                                )
                                        )
                                )

                                // Set max count
                                .then(CommandManager.literal("setcount")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("maxCount", IntegerArgumentType.integer(1))
                                                        .executes(SpawnViolentNpcCommand::setMaxCount)
                                                )
                                        )
                                )

                                // Set respawn delay
                                .then(CommandManager.literal("setrespawn")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                                        .executes(SpawnViolentNpcCommand::setRespawnDelay)
                                                )
                                        )
                                )

                                // Force spawn
                                .then(CommandManager.literal("spawn")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .executes(SpawnViolentNpcCommand::spawn)
                                        )
                                )

                                // Despawn
                                .then(CommandManager.literal("despawn")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .executes(SpawnViolentNpcCommand::despawn)
                                        )
                                )

                                // List all
                                .then(CommandManager.literal("list")
                                        .executes(SpawnViolentNpcCommand::list)
                                )

                                // Remove
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                                .executes(SpawnViolentNpcCommand::remove)
                                        )
                                )
                        ));
    }

    private static int setPos1(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        Vec3d pos = player.getEntityPos();
        SelectionManager.setPos1(player, pos);

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aFirst position set to §f" + formatVec3d(pos)
        ), false);

        // Show selection info if both positions are set
        if (SelectionManager.hasCompleteSelection(player)) {
            showSelectionInfo(ctx, player);
        }

        return 1;
    }

    private static int setPos2(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        Vec3d pos = player.getEntityPos();
        SelectionManager.setPos2(player, pos);

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aSecond position set to §f" + formatVec3d(pos)
        ), false);

        // Show selection info if both positions are set
        if (SelectionManager.hasCompleteSelection(player)) {
            showSelectionInfo(ctx, player);
        }

        return 1;
    }

    private static int showSelection(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        SelectionManager.Selection sel = SelectionManager.getSelection(player);

        if (!sel.isComplete()) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§7Current selection:"
            ), false);

            sel.getPos1().ifPresentOrElse(
                    p -> ctx.getSource().sendFeedback(() -> Text.literal("§aPos1: §f" + formatVec3d(p)), false),
                    () -> ctx.getSource().sendFeedback(() -> Text.literal("§7Pos1: §cnot set"), false)
            );

            sel.getPos2().ifPresentOrElse(
                    p -> ctx.getSource().sendFeedback(() -> Text.literal("§aPos2: §f" + formatVec3d(p)), false),
                    () -> ctx.getSource().sendFeedback(() -> Text.literal("§7Pos2: §cnot set"), false)
            );

            return 0;
        }

        showSelectionInfo(ctx, player);
        return 1;
    }

    private static void showSelectionInfo(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
        SelectionManager.Selection sel = SelectionManager.getSelection(player);
        Vec3d min = sel.getMin();
        Vec3d max = sel.getMax();
        int volume = sel.getVolume();

        ctx.getSource().sendFeedback(() -> Text.literal("§e=== Selection Info ==="), false);
        ctx.getSource().sendFeedback(() -> Text.literal("§aMin: §f" + formatVec3d(min)), false);
        ctx.getSource().sendFeedback(() -> Text.literal("§aMax: §f" + formatVec3d(max)), false);
        ctx.getSource().sendFeedback(() -> Text.literal("§aVolume: §f" + volume + " blocks³"), false);
    }

    private static int clearSelection(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        SelectionManager.clearSelection(player);
        ctx.getSource().sendFeedback(() -> Text.literal("§aSelection cleared"), false);
        return 1;
    }

    private static int create(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String entityType = StringArgumentType.getString(ctx, "entityType");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        // Require complete selection
        if (!SelectionManager.hasCompleteSelection(player)) {
            ctx.getSource().sendError(Text.literal("§cYou must set both positions first! Use /npc violent pos1 and pos2"));
            return 0;
        }

        SelectionManager.Selection sel = SelectionManager.getSelection(player);
        Vec3d min = sel.getMin();
        Vec3d max = sel.getMax();

        ViolentNpcRegistry.SpawnArea area = new ViolentNpcRegistry.SpawnArea(
                min, max, 1, 200
        );

        int id = ViolentNpcRegistry.registerNpc(name, entityType, area);

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aCreated violent NPC #" + id + " §f(" + name + ")§a in selected area"
        ), false);
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§7Use §f/npc violent setstats " + id + " <health> <damage> <speed>§7 to configure"
        ), false);

        return 1;
    }

    private static int setArea(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        var npcOpt = ViolentNpcRegistry.getNpcById(Optional.of(id));
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        if (!SelectionManager.hasCompleteSelection(player)) {
            ctx.getSource().sendError(Text.literal("§cYou must set both positions first! Use /npc violent pos1 and pos2"));
            return 0;
        }

        SelectionManager.Selection sel = SelectionManager.getSelection(player);
        Vec3d min = sel.getMin();
        Vec3d max = sel.getMax();

        ViolentNpcRegistry.ViolentNpcData npc = npcOpt.get();
        npc.spawnArea.minX = min.x;
        npc.spawnArea.minY = min.y;
        npc.spawnArea.minZ = min.z;
        npc.spawnArea.maxX = max.x;
        npc.spawnArea.maxY = max.y;
        npc.spawnArea.maxZ = max.z;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aUpdated spawn area for NPC #" + id
        ), false);
        ctx.getSource().sendFeedback(() -> Text.literal(
                "§7Min: §f" + formatVec3d(min) + " §7Max: §f" + formatVec3d(max)
        ), false);

        return 1;
    }

    private static int setStats(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        double health = DoubleArgumentType.getDouble(ctx, "health");
        double damage = DoubleArgumentType.getDouble(ctx, "damage");
        double speed = DoubleArgumentType.getDouble(ctx, "speed") / 10.0;

        var npcOpt = ViolentNpcRegistry.getNpcById(Optional.of(id));
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        ViolentNpcRegistry.Stats stats = new ViolentNpcRegistry.Stats(health, damage, speed, 0.0, 16.0);
        ViolentNpcRegistry.setStats(id, stats);

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aSet stats for NPC #" + id + ": §fHP=" + health + " DMG=" + damage + " SPD=" + speed
        ), false);

        return 1;
    }

    private static int setMaxCount(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        int maxCount = IntegerArgumentType.getInteger(ctx, "maxCount");

        var npcOpt = ViolentNpcRegistry.getNpcById(Optional.of(id));
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        npcOpt.get().spawnArea.maxCount = maxCount;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aSet max count for NPC #" + id + " to " + maxCount
        ), false);

        return 1;
    }

    private static int setRespawnDelay(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        var npcOpt = ViolentNpcRegistry.getNpcById(Optional.of(id));
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        npcOpt.get().spawnArea.respawnDelayTicks = seconds * 20;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal(
                "§aSet respawn delay for NPC #" + id + " to " + seconds + " seconds"
        ), false);

        return 1;
    }

    private static int spawn(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        ServerWorld world = ctx.getSource().getWorld();

        ViolentNpcTracker.spawnNpc(world, id);
        ctx.getSource().sendFeedback(() -> Text.literal("§aSpawned NPC #" + id), false);

        return 1;
    }

    private static int despawn(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        ServerWorld world = ctx.getSource().getWorld();

        ViolentNpcTracker.despawn(world, id);
        ctx.getSource().sendFeedback(() -> Text.literal("§aDespawned all entities for NPC #" + id), false);

        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        var npcs = ViolentNpcRegistry.getAllNpcs();

        if (npcs.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("§7No violent NPCs registered"), false);
            return 0;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("§e=== Violent NPCs ==="), false);
        for (var npc : npcs) {
            Vec3d min = new Vec3d(npc.spawnArea.minX, npc.spawnArea.minY, npc.spawnArea.minZ);
            Vec3d max = new Vec3d(npc.spawnArea.maxX, npc.spawnArea.maxY, npc.spawnArea.maxZ);

            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§a#" + npc.id + " §f" + npc.name + " §7(" + npc.entityType + ")"
            ), false);
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "  §7HP:" + npc.stats.health + " DMG:" + npc.stats.attackDamage +
                            " Count:" + npc.spawnArea.maxCount + " Respawn:" + (npc.spawnArea.respawnDelayTicks / 20) + "s"
            ), false);
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "  §7Area: " + formatVec3d(min) + " to " + formatVec3d(max)
            ), false);
        }

        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        ServerWorld world = ctx.getSource().getWorld();

        ViolentNpcTracker.despawn(world, id);
        ViolentNpcRegistry.removeNpcById(id);

        ctx.getSource().sendFeedback(() -> Text.literal("§aRemoved violent NPC #" + id), false);

        return 1;
    }

    private static String formatVec3d(Vec3d vec) {
        return String.format("(%.1f, %.1f, %.1f)", vec.x, vec.y, vec.z);
    }
}