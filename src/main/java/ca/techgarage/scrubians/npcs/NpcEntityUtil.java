package ca.techgarage.scrubians.npcs;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public final class NpcEntityUtil {

    public static TrackingMannequinEntity getMannequinById(ServerWorld world, int npcId) {
        Box worldBox = new Box(
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY
        );

        for (TrackingMannequinEntity mannequin :
                world.getEntitiesByClass(TrackingMannequinEntity.class, worldBox, e -> true)) {

            if (mannequin.getNpcId() == npcId) {
                return mannequin;
            }
        }
        return null;
    }

    private NpcEntityUtil() {}
}
