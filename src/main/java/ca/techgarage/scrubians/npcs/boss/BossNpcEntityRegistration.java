package ca.techgarage.scrubians.npcs.boss;

import ca.techgarage.scrubians.events.BossNpcDeathCallback;
import ca.techgarage.scrubians.npcs.boss.BossNpcEntity;
import ca.techgarage.scrubians.npcs.boss.BossNpcRegistry;
import ca.techgarage.scrubians.npcs.boss.BossNpcTracker;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

/**
 * Event handler for Boss NPC deaths
 * Register this in your mod initialization
 */
public class BossNpcEntityRegistration {

    /**
     * Register event handlers
     * Call this in your mod's onInitialize() method
     */
    public static void register() {
        // Listen for entity deaths
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getEntityWorld() instanceof ServerWorld serverWorld) {
                // Check if this is a Boss NPC using the spawner
                if (BossNpcEntity.isBossNpc(entity)) {
                    int npcId = BossNpcEntity.getNpcId(entity).orElse(-1);

                    if (npcId != -1) {
                        // Get NPC data for the event
                        Optional<BossNpcRegistry.BossNpcData> npcDataOpt =
                                BossNpcRegistry.getNpcById(Optional.of(npcId));

                        String name = "Unknown";
                        String entityType = String.valueOf(BossNpcEntity.getBaseType(entity));

                        if (npcDataOpt.isPresent()) {
                            BossNpcRegistry.BossNpcData npcData = npcDataOpt.get();
                            name = npcData.name;
                            if (entityType.isEmpty()) {
                                entityType = npcData.entityType;
                            }
                        }

                        // Fire the death event for other mods to listen to
                        BossNpcDeathCallback.EVENT.invoker().onBossNpcDeath(
                                serverWorld,
                                entity,
                                npcId,
                                name,
                                entityType,
                                damageSource
                        );

                        // Notify the tracker for respawning
                        BossNpcTracker.notifyNpcDeath(serverWorld, npcId, entity.getUuid());
                    }
                }
            }
        });
    }
}