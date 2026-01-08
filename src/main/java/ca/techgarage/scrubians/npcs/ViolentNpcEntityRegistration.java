package ca.techgarage.scrubians.npcs;

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
                    Optional<Integer> npcId = ViolentNpcEntity.getNpcId(entity);
                    ViolentNpcTracker.notifyNpcDeath(serverWorld, npcId, entity.getUuid());
                }
            }
        });
    }
}