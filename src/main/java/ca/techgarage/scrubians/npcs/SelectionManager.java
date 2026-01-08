package ca.techgarage.scrubians.npcs;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages WorldEdit-style position selections for players
 * Players can select two corners to define a cuboid region
 */
public class SelectionManager {

    private static final Map<UUID, Selection> PLAYER_SELECTIONS = new HashMap<>();

    public static class Selection {
        private Vec3d pos1;
        private Vec3d pos2;

        public Selection() {
            this.pos1 = null;
            this.pos2 = null;
        }

        public void setPos1(Vec3d pos) {
            this.pos1 = pos;
        }

        public void setPos2(Vec3d pos) {
            this.pos2 = pos;
        }

        public Optional<Vec3d> getPos1() {
            return Optional.ofNullable(pos1);
        }

        public Optional<Vec3d> getPos2() {
            return Optional.ofNullable(pos2);
        }

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }

        public void clear() {
            pos1 = null;
            pos2 = null;
        }

        /**
         * Gets the minimum corner of the selection (lowest x, y, z)
         */
        public Vec3d getMin() {
            if (!isComplete()) return null;
            return new Vec3d(
                    Math.min(pos1.x, pos2.x),
                    Math.min(pos1.y, pos2.y),
                    Math.min(pos1.z, pos2.z)
            );
        }

        /**
         * Gets the maximum corner of the selection (highest x, y, z)
         */
        public Vec3d getMax() {
            if (!isComplete()) return null;
            return new Vec3d(
                    Math.max(pos1.x, pos2.x),
                    Math.max(pos1.y, pos2.y),
                    Math.max(pos1.z, pos2.z)
            );
        }

        public int getVolume() {
            if (!isComplete()) return 0;
            Vec3d min = getMin();
            Vec3d max = getMax();
            return (int) Math.ceil((max.x - min.x) * (max.y - min.y) * (max.z - min.z));
        }
    }

    /**
     * Gets or creates a selection for a player
     */
    public static Selection getSelection(ServerPlayerEntity player) {
        return PLAYER_SELECTIONS.computeIfAbsent(player.getUuid(), k -> new Selection());
    }

    /**
     * Sets position 1 for a player
     */
    public static void setPos1(ServerPlayerEntity player, Vec3d pos) {
        getSelection(player).setPos1(pos);
    }

    /**
     * Sets position 2 for a player
     */
    public static void setPos2(ServerPlayerEntity player, Vec3d pos) {
        getSelection(player).setPos2(pos);
    }

    /**
     * Clears a player's selection
     */
    public static void clearSelection(ServerPlayerEntity player) {
        getSelection(player).clear();
    }

    /**
     * Checks if a player has a complete selection
     */
    public static boolean hasCompleteSelection(ServerPlayerEntity player) {
        return getSelection(player).isComplete();
    }

    /**
     * Removes a player's selection entirely (on disconnect, etc.)
     */
    public static void removePlayer(UUID playerUuid) {
        PLAYER_SELECTIONS.remove(playerUuid);
    }
}