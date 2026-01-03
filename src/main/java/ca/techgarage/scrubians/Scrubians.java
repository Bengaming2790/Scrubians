package ca.techgarage.scrubians;

import ca.techgarage.scrubians.commands.*;
import ca.techgarage.scrubians.dialogue.DialogueActionCommand;
import ca.techgarage.scrubians.events.ChunkLoadCleanup;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.TrackingMannequinEntity;
import ca.techgarage.scrubians.npcs.ViolentNpcRegistry;
import ca.techgarage.scrubians.npcs.ViolentNpcTracker;
import net.fabricmc.api.ModInitializer;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ca.techgarage.scrubians.commands.NPCKillInvalidCommand;

import static ca.techgarage.scrubians.commands.NPCRespawnCommand.respawnAllOnServerStart;

public class Scrubians implements ModInitializer {

    public static ScrubiansConfig CONFIG;

    private static int cleanupTickCounter = 0;
    private static boolean hasSpawnedNPCsOnStartup = false; // Track if we've done initial spawn

    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register(SpawnNpcCommand::register);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCListCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCEditCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCRespawnCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DebugFileLocationCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCRecoveryCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCRemoveAllCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueTestCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueActionCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueEditCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueActionTestCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCDiagnoseCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCCleanupJsonCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CleanupStatsCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NPCKillInvalidCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register(SpawnViolentNpcCommand::register);
        AutoConfig.register(ScrubiansConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ScrubiansConfig.class).getConfig();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Use current working directory (where the server is actually running)
            File serverRoot = new File(".").getAbsoluteFile();
            System.out.println("[Scrubians] Server root directory: " + serverRoot.getAbsolutePath());
            NpcRegistry.init(serverRoot);
            ViolentNpcRegistry.init(serverRoot);

            // Respawn all NPCs from registry ONCE after a short delay
            server.execute(() -> {
                try {
                    Thread.sleep(1000); // 1 second delay to ensure world is fully loaded
                    System.out.println("[Scrubians] Initial NPC spawn on server start");
                    for (ServerWorld world : server.getWorlds()) {
                        respawnAllOnServerStart(world);
                    }
                    hasSpawnedNPCsOnStartup = true;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });

        // Save periodically during server tick
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            LoadedChunkTracker.onLoad(world, chunk);
            ChunkLoadCleanup.onChunkLoad(world, chunk);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            LoadedChunkTracker.onUnload(world, chunk);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            cleanupTickCounter++;

            // Run cleanup every 2 seconds (40 ticks)
            if (cleanupTickCounter >= 40) {
                cleanupTickCounter = 0;

                // Kill invalid NPCs (those without proper registry data)
                for (ServerWorld world : server.getWorlds()) {
                    NPCKillInvalidCommand.killInvalidNPCs(world);
                }

                // Run chunk cleanup
                for (ServerWorld world : server.getWorlds()) {
                    for (long pos : LoadedChunkTracker.getLoadedChunks()) {
                        WorldChunk chunk = world.getChunkManager().getWorldChunk(
                                ChunkPos.getPackedX(pos),
                                ChunkPos.getPackedZ(pos)
                        );

                        if (chunk != null) {
                            ChunkLoadCleanup.onChunkLoad(world, chunk);
                        }
                    }
                }

                for (ServerWorld world : server.getWorlds()) {
                    ViolentNpcTracker.tick(world);
                }
                // REMOVED: Don't constantly respawn NPCs every 2 seconds!
                // Only respawn on server start (handled in SERVER_STARTED event)
                // If you need to respawn, use the /npcrespawn command
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            System.out.println("[Scrubians] Server stopping, saving NPC data...");
            NpcRegistry.forceSave();
            hasSpawnedNPCsOnStartup = false; // Reset for next server start
            ViolentNpcRegistry.forceSave();
            ViolentNpcTracker.clear();
        });

        System.out.println("Scrubians - loaded");
    }

    public static ScrubiansConfig getConfig() {
        return CONFIG;
    }
}