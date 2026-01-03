package ca.techgarage.scrubians.events;

import ca.techgarage.scrubians.npcs.ViolentNpcTracker;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;

/**
 * Listens for entity deaths to trigger violent NPC respawning
 */
public class ViolentNpcDeathListener {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(ViolentNpcDeathListener::onEntityDeath);
    }

    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity.getEntityWorld() instanceof ServerWorld) {
            ViolentNpcTracker.onEntityDeath(entity);
        }
    }
}

// Add this to your Scrubians.java onInitialize():
// ViolentNpcDeathListener.register();