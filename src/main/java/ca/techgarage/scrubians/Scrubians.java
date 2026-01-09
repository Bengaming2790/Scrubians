package ca.techgarage.scrubians;

import ca.techgarage.scrubians.commands.*;
import ca.techgarage.scrubians.dialogue.DialogueActionCommand;
import ca.techgarage.scrubians.events.ChunkLoadCleanup;
import ca.techgarage.scrubians.events.ViolentNpcChunkCleanup;
import ca.techgarage.scrubians.npcs.*;
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

public class Scrubians implements ModInitializer {

    public static ScrubiansConfig CONFIG;
    public static final String MOD_ID = "scrubians";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final boolean DEVELOPER_MODE = true;
    private static int cleanupTickCounter = 0;
    private static boolean hasSpawnedNPCsOnStartup = false;

    @Override
    public void onInitialize() {
        // Register commands
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

        ViolentNpcEntityRegistration.register();

        if (DEVELOPER_MODE) {
            logger("warning", "[Scrubians] Developer mode is ON - Initializing unreleased features.");
            CommandRegistrationCallback.EVENT.register(SpawnViolentNpcCommand::register);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            File serverRoot = new File(".").getAbsoluteFile();
            LOGGER.info("[Scrubians] Server root directory: " + serverRoot.getAbsolutePath());
            NpcRegistry.init(serverRoot);

            if (DEVELOPER_MODE) {
                ViolentNpcRegistry.init(serverRoot);
            }

            // Clean up and respawn NPCs after a short delay
            server.execute(() -> {
                try {
                    Thread.sleep(1000); // 1 second delay
                    LOGGER.info("[Scrubians] Starting NPC initialization on server start");

                    for (ServerWorld world : server.getWorlds()) {
                        // Clean up existing violent NPCs first
                        if (DEVELOPER_MODE) {
                            ViolentNpcChunkCleanup.cleanupAllViolentNpcs(world);
                        }

                        // Spawn regular NPCs
                        respawnAllOnServerStart(world);

                        // Spawn violent NPCs
                        if (DEVELOPER_MODE) {
                            ViolentNpcTracker.initializeAllNpcs(world);
                        }
                    }

                    hasSpawnedNPCsOnStartup = true;
                    LOGGER.info("[Scrubians] NPC initialization complete");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });

        // Chunk load/unload events
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            LoadedChunkTracker.onLoad(world, chunk);
            ChunkLoadCleanup.onChunkLoad(world, chunk);

            if (DEVELOPER_MODE) {
                ViolentNpcChunkCleanup.onChunkLoad(world, chunk);
            }
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            LoadedChunkTracker.onUnload(world, chunk);
        });

        // Server tick events - FIXED: Properly wrapped in DEVELOPER_MODE check
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (DEVELOPER_MODE) {
                for (ServerWorld world : server.getWorlds()) {
                    ViolentNpcEntity.tickFireImmunity(world);
                    ViolentNpcTracker.tick(world);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            cleanupTickCounter++;

            // Run cleanup every 2 seconds (40 ticks)
            if (cleanupTickCounter >= 40) {
                cleanupTickCounter = 0;

                // Kill invalid NPCs
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

                            if (DEVELOPER_MODE) {
                                ViolentNpcChunkCleanup.onChunkLoad(world, chunk);
                            }
                        }
                    }
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[Scrubians] Server stopping, saving NPC data...");
            NpcRegistry.forceSave();
            hasSpawnedNPCsOnStartup = false;

            if (DEVELOPER_MODE) {
                ViolentNpcRegistry.forceSave();
                ViolentNpcTracker.clear();
                ViolentNpcChunkCleanup.reset();
            }
        });

        LOGGER.info("[Scrubians] Loaded");
    }

    public static void logger(String type, String log) {
        if (type.equalsIgnoreCase("warning")) {
            LOGGER.warn(log);
        } else if (type.equalsIgnoreCase("error")) {
            LOGGER.error(log);
        } else {
            LOGGER.info(log);
        }
    }

    public static void logger(String log) {
        LOGGER.info(log);
    }

    public static ScrubiansConfig getConfig() {
        return CONFIG;
    }
}