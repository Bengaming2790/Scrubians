package ca.techgarage.scrubians.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;

public interface BossNpcDeathCallback {

    Event<BossNpcDeathCallback> EVENT = EventFactory.createArrayBacked(
            BossNpcDeathCallback.class,
            (listeners) -> (world, entity, npcId, name, entityType, damageSource) -> {
                for (BossNpcDeathCallback listener : listeners) {
                    listener.onBossNpcDeath(world, entity, npcId, name, entityType, damageSource);
                }
            }
    );

    /**
     * Called when a violent NPC dies
     *
     * @param world The world the NPC died in
     * @param entity The entity that died
     * @param npcId The NPC's registry ID
     * @param name The NPC's custom name
     * @param entityType The base entity type (e.g., "zombie", "skeleton")
     * @param damageSource The source of damage that killed the NPC
     */
    void onBossNpcDeath(
            ServerWorld world,
            Entity entity,
            int npcId,
            String name,
            String entityType,
            DamageSource damageSource
    );

}
