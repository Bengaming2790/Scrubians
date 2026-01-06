package ca.techgarage.scrubians.commands;

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

/**
 * Command to create and manage violent NPCs
 * Usage examples:
 * /npc violent create <name> <entityType> - Start creating a new violent NPC
 * /npc violent setcorner1 <id> - Set first corner of spawn area at player position
 * /npc violent setcorner2 <id> - Set second corner of spawn area
 * /npc violent setstats <id> <health> <damage> <speed> - Set NPC stats
 * /npc violent setcount <id> <maxCount> - Set max spawn count
 * /npc violent setrespawn <id> <seconds> - Set respawn delay
 * /npc violent spawn <id> - Force spawn the NPC
 * /npc violent despawn <id> - Despawn all entities for NPC
 * /npc violent list - List all violent NPCs
 * /npc violent remove <id> - Remove a violent NPC
 */

public class SpawnViolentNpcCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .requires(source -> source.hasPermissionLevel(2)).then(CommandManager.literal("violent")

                        // Create new violent NPC
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("name", StringArgumentType.string())
                                        .then(CommandManager.argument("entityType", StringArgumentType.string())
                                                .executes(SpawnViolentNpcCommand::create)
                                        )
                                )
                        )

                        // Set spawn area corner 1
                        .then(CommandManager.literal("setcorner1")
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                        .executes(SpawnViolentNpcCommand::setCorner1)
                                )
                        )

                        // Set spawn area corner 2
                        .then(CommandManager.literal("setcorner2")
                                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                                        .executes(SpawnViolentNpcCommand::setCorner2)
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

    private static int create(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String entityType = StringArgumentType.getString(ctx, "entityType");

        // Create with default spawn area at player position
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        Vec3d pos = player.getEntityPos();
        ViolentNpcRegistry.SpawnArea area = new ViolentNpcRegistry.SpawnArea(
                pos.add(-5, -2, -5),
                pos.add(5, 2, 5),
                1,
                200
        );

        int id = ViolentNpcRegistry.registerNpc(name, entityType, area);
        ctx.getSource().sendFeedback(() -> Text.literal("§aCreated violent NPC #" + id + " (" + name + ")"), false);
        ctx.getSource().sendFeedback(() -> Text.literal("§7Use /spawnviolentnpc setcorner1 " + id + " and setcorner2 " + id + " to define spawn area"), false);

        return 1;
    }

    private static int setCorner1(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        var npcOpt = ViolentNpcRegistry.getNpcById(id);
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        Vec3d pos = player.getEntityPos();
        ViolentNpcRegistry.ViolentNpcData npc = npcOpt.get();
        npc.spawnArea.minX = pos.x;
        npc.spawnArea.minY = pos.y;
        npc.spawnArea.minZ = pos.z;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal("§aSet corner 1 for NPC #" + id + " at " + pos), false);
        return 1;
    }

    private static int setCorner2(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");

        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("Must be a player!"));
            return 0;
        }

        var npcOpt = ViolentNpcRegistry.getNpcById(id);
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        Vec3d pos = player.getEntityPos();
        ViolentNpcRegistry.ViolentNpcData npc = npcOpt.get();
        npc.spawnArea.maxX = pos.x;
        npc.spawnArea.maxY = pos.y;
        npc.spawnArea.maxZ = pos.z;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal("§aSet corner 2 for NPC #" + id + " at " + pos), false);
        return 1;
    }

    private static int setStats(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        double health = DoubleArgumentType.getDouble(ctx, "health");
        double damage = DoubleArgumentType.getDouble(ctx, "damage");
        double speed = DoubleArgumentType.getDouble(ctx, "speed");

        var npcOpt = ViolentNpcRegistry.getNpcById(id);
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        ViolentNpcRegistry.Stats stats = new ViolentNpcRegistry.Stats(health, damage, speed, 0.0, 16.0);
        ViolentNpcRegistry.setStats(id, stats);

        ctx.getSource().sendFeedback(() -> Text.literal("§aSet stats for NPC #" + id + ": HP=" + health + ", DMG=" + damage + ", SPD=" + speed), false);
        return 1;
    }

    private static int setMaxCount(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        int maxCount = IntegerArgumentType.getInteger(ctx, "maxCount");

        var npcOpt = ViolentNpcRegistry.getNpcById(id);
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        npcOpt.get().spawnArea.maxCount = maxCount;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal("§aSet max count for NPC #" + id + " to " + maxCount), false);
        return 1;
    }

    private static int setRespawnDelay(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        var npcOpt = ViolentNpcRegistry.getNpcById(id);
        if (npcOpt.isEmpty()) {
            ctx.getSource().sendError(Text.literal("NPC #" + id + " not found!"));
            return 0;
        }

        npcOpt.get().spawnArea.respawnDelayTicks = seconds * 20;
        ViolentNpcRegistry.forceSave();

        ctx.getSource().sendFeedback(() -> Text.literal("§aSet respawn delay for NPC #" + id + " to " + seconds + " seconds"), false);
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

        ViolentNpcTracker.despawnNpc(world, id);
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
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§a#" + npc.id + " §f" + npc.name + " §7(" + npc.entityType + ") " +
                            "HP:" + npc.stats.health + " DMG:" + npc.stats.attackDamage + " Count:" + npc.spawnArea.maxCount
            ), false);
        }

        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        ServerWorld world = ctx.getSource().getWorld();

        ViolentNpcTracker.despawnNpc(world, id);
        ViolentNpcRegistry.removeNpcById(id);

        ctx.getSource().sendFeedback(() -> Text.literal("§aRemoved violent NPC #" + id), false);
        return 1;
    }
}