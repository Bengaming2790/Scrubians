package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.ScrubiansPermissions;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class NpcKillInvalidCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("npc")
                        .then(net.minecraft.server.command.CommandManager.literal("killInvalid")
                                .requires(src -> ScrubiansPermissions.has(src, "scrubians.npc.killinvalid"))
                                .executes(ctx -> execute(ctx.getSource()))
                        )
        );
    }

    private static int execute(ServerCommandSource source) {
        int removed = 0;

        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof MannequinEntity mannequin) {

                    // Kill all non-NPC mannequins
                    if (!(mannequin instanceof TrackingMannequinEntity)) {
                        mannequin.discard();
                    }
                }
            }
        }


        int finalRemoved = removed;

        source.sendFeedback(
                () -> Text.literal("[Scrubians] Removed " + finalRemoved + " invalid NPC(s)."),
                true
        );

        return removed;
    }


    public static void killInvalidNPCs(ServerWorld world) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof MannequinEntity mannequin) {

                    // Kill all non-NPC mannequins
                    if (!(mannequin instanceof TrackingMannequinEntity)) {
                        mannequin.discard();
                    }
                }
            }
        }



}
