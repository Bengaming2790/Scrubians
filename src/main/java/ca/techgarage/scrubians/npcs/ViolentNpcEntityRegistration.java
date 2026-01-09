package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.events.ViolentNpcDeathCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

/**
 * Event handler for violent NPC deaths
 * Register this in your mod initialization
 */
public class ViolentNpcEntityRegistration {

    /**
     * Register event handlers
     * Call this in your mod's onInitialize() method
     */
    public static void register() {
        // Listen for entity deaths
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getEntityWorld() instanceof ServerWorld serverWorld) {
                // Check if this is a violent NPC using the spawner
                if (ViolentNpcEntity.isViolentNpc(entity)) {
                    int npcId = ViolentNpcEntity.getNpcId(entity).orElse(-1);

                    if (npcId != -1) {
                        // Get NPC data for the event
                        Optional<ViolentNpcRegistry.ViolentNpcData> npcDataOpt =
                                ViolentNpcRegistry.getNpcById(Optional.of(npcId));

                        String name = "Unknown";
                        String entityType = String.valueOf(ViolentNpcEntity.getBaseType(entity));

                        if (npcDataOpt.isPresent()) {
                            ViolentNpcRegistry.ViolentNpcData npcData = npcDataOpt.get();
                            name = npcData.name;
                            if (entityType.isEmpty()) {
                                entityType = npcData.entityType;
                            }
                        }

                        // Fire the death event for other mods to listen to
                        ViolentNpcDeathCallback.EVENT.invoker().onViolentNpcDeath(
                                serverWorld,
                                entity,
                                npcId,
                                name,
                                entityType,
                                damageSource
                        );

                        // Notify the tracker for respawning
                        ViolentNpcTracker.notifyNpcDeath(serverWorld, npcId, entity.getUuid());
                    }
                }
            }
        });
    }
}