package ca.techgarage.scrubians.commands;

import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.NpcRegistry.NpcData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class NPCListCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("npc")
                        .requires(source -> source.hasPermissionLevel(2)).then(CommandManager.literal("list")
                        .executes(NPCListCommand::execute))
        );
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (NpcRegistry.getAllNpcs().isEmpty()) {
            source.sendFeedback(
                    () -> Text.literal("No NPCs registered."),
                    false
            );
            return 1;
        }

        source.sendFeedback(
                () -> Text.literal("All NPCs:"),
                false
        );

        for (NpcData npc : NpcRegistry.getAllNpcs()) {
            Vec3d pos = npc.getPosition();

            Text teleportText = Text.literal("[Teleport]")
                    .styled(style -> style
                            .withColor(0x55FF55)
                            .withClickEvent(
                                    new ClickEvent.RunCommand(
                                            "/tp @s " + pos.x + " " + pos.y + " " + pos.z
                                    )
                            )
                    );

            Text line = Text.literal("Id: " + npc.id + " | Name: " + npc.name + " ")
                    .append(teleportText);

            source.sendFeedback(() -> line, false);
        }


        return 1;
    }
}
