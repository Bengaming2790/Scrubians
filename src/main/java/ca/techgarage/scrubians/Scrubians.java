package ca.techgarage.scrubians;

import ca.techgarage.scrubians.commands.*;
import ca.techgarage.scrubians.dialogue.DialogueActionCommand;
import ca.techgarage.scrubians.events.ChunkLoadCleanup;
import ca.techgarage.scrubians.npcs.NpcRegistry;
import ca.techgarage.scrubians.npcs.ViolentNpcRegistry;
import ca.techgarage.scrubians.npcs.ViolentNpcTracker;
import net.fabricmc.api.ModInitializer;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

import ca.techgarage.scrubians.commands.NpcKillInvalidCommand;

import static ca.techgarage.scrubians.commands.NpcRespawnCommand.respawnAllOnServerStart;

/**
 * The type Scrubians.
 */
public class Scrubians implements ModInitializer {

    /**
     * The constant CONFIG.
     */
    public static ScrubiansConfig CONFIG;

    public static final String MOD_ID = "scrubians";
    
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final boolean DEVELOPER_MODE = false;

    private static int cleanupTickCounter = 0;
    private static boolean hasSpawnedNPCsOnStartup = false; // Track if we've done initial spawn

    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register(SpawnNpcCommand::register);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcListCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcEditCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcRespawnCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DebugFileLocationCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcRecoveryCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcRemoveAllCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueTestCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueActionCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueEditCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DialogueActionTestCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcDiagnoseCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcCleanupJsonCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CleanupStatsCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcKillInvalidCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcHelpCommand.register(dispatcher));
        AutoConfig.register(ScrubiansConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ScrubiansConfig.class).getConfig();

        if (DEVELOPER_MODE) {
            Logger.info("[Scrubians] Developer mode is ON - Initializing unreleased features.");
            CommandRegistrationCallback.EVENT.register(SpawnViolentNpcCommand::register);

        }


        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Use current working directory (where the server is actually running)
            File serverRoot = new File(".").getAbsoluteFile();
            Logger.info("[Scrubians] Server root directory: " + serverRoot.getAbsolutePath());
            NpcRegistry.init(serverRoot);
            ViolentNpcRegistry.init(serverRoot);

            // Respawn all NPCs from registry ONCE after a short delay
            server.execute(() -> {
                try {
                    Thread.sleep(1000); // 1 second delay to ensure world is fully loaded
                    Logger.info("[Scrubians] Initial NPC spawn on server start");
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
                    NpcKillInvalidCommand.killInvalidNPCs(world);
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

            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Logger.info("[Scrubians] Server stopping, saving NPC data...");
            NpcRegistry.forceSave();
            hasSpawnedNPCsOnStartup = false; // Reset for next server start
            ViolentNpcRegistry.forceSave();
            ViolentNpcTracker.clear();
        });

        Logger.info("[Scrubians] Loaded");
    }

    /**
     * Gets config.
     *
     * @return the config
     */

    public static void logger(String type, String log) {

        if (type.equalsIgnoreCase("warning") {
            Logger.warn(log);
        } else if (type.equalsIgnoreCase("error")) {
            Logger.error(log);
        } else {
            Logger.info(log);
        } 
        
    }
    
    
    public static ScrubiansConfig getConfig() {
        return CONFIG;
    }
}
