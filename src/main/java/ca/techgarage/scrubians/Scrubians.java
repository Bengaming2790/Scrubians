package ca.techgarage.scrubians;

import ca.techgarage.scrubians.commands.*;
import ca.techgarage.scrubians.dialogue.DialogueActionCommand;
import ca.techgarage.scrubians.events.ChunkLoadCleanup;
import ca.techgarage.scrubians.events.ViolentNpcChunkCleanup;
import ca.techgarage.scrubians.npcs.*;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcEntity;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcEntityRegistration;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcRegistry;
import ca.techgarage.scrubians.npcs.violent.ViolentNpcTracker;
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
    public static final boolean DEVELOPER_MODE = false;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcRemoveCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> NpcReloadCommand.register(dispatcher));

        AutoConfig.register(ScrubiansConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ScrubiansConfig.class).getConfig();

        ViolentNpcEntityRegistration.register();


            logger("warning", "[Scrubians] Developer mode is ON - Initializing unreleased features.");
            CommandRegistrationCallback.EVENT.register(SpawnViolentNpcCommand::register);


        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            File serverRoot = new File(".").getAbsoluteFile();
            logger("[Scrubians] Server root directory: " + serverRoot.getAbsolutePath());
            NpcRegistry.init(serverRoot);

                ViolentNpcRegistry.init(serverRoot);


            // Clean up and respawn NPCs after a short delay
            server.execute(() -> {
                try {
                    Thread.sleep(1000);
                    logger("[Scrubians] Starting NPC initialization on server start");

                    for (ServerWorld world : server.getWorlds()) {

                        ViolentNpcTracker.initializeAllNpcs(world);
                        respawnAllOnServerStart(world);
                    }
                    hasSpawnedNPCsOnStartup = true;
                    logger("[Scrubians] NPC initialization complete");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });

        // Chunk load/unload events
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            LoadedChunkTracker.onLoad(world, chunk);
            ChunkLoadCleanup.onChunkLoad(world, chunk);
            ViolentNpcChunkCleanup.onChunkLoad(world, chunk);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register(LoadedChunkTracker::onUnload);

        ServerTickEvents.START_SERVER_TICK.register(server -> {
                for (ServerWorld world : server.getWorlds()) {
                    ViolentNpcEntity.tickFireImmunity(world);
                    ViolentNpcTracker.tick(world);
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
                            ViolentNpcChunkCleanup.onChunkLoad(world, chunk);
                        }
                    }
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            logger("[Scrubians] Server stopping, despawning violent NPCs...");

            for (ServerWorld world : server.getWorlds()) {
                ViolentNpcTracker.despawnAllViolentNpcs(world);
            }

            NpcRegistry.forceSave();
            ViolentNpcRegistry.forceSave();
        });


        logger("[Scrubians] Loaded");
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