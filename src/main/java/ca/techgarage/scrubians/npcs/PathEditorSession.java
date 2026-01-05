package ca.techgarage.scrubians.npcs;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import ca.techgarage.scrubians.Scrubians;

import java.util.*;

/**
 * Manages active path editing sessions for players
 */
public class PathEditorSession {

    private static final Map<UUID, PathEditorSession> ACTIVE_SESSIONS = new HashMap<>();

    private final UUID playerUUID;
    private final int npcId;
    private final List<NpcRegistry.Waypoint> waypoints;
    private int defaultWaitTicks;

    private PathEditorSession(UUID playerUUID, int npcId) {
        this.playerUUID = playerUUID;
        this.npcId = npcId;
        this.waypoints = new ArrayList<>();
        this.defaultWaitTicks = Scrubians.CONFIG.defaultNPCWaitTimeInTicks;
    }

    /**
     * Start a new path editing session for a player
     */
    public static PathEditorSession start(ServerPlayerEntity player, int npcId) {
        UUID uuid = player.getUuid();

        // End any existing session
        if (ACTIVE_SESSIONS.containsKey(uuid)) {
            ACTIVE_SESSIONS.get(uuid).cancel();
        }

        PathEditorSession session = new PathEditorSession(uuid, npcId);
        ACTIVE_SESSIONS.put(uuid, session);
        return session;
    }

    /**
     * Get the active session for a player, if any
     */
    public static Optional<PathEditorSession> getSession(ServerPlayerEntity player) {
        return Optional.ofNullable(ACTIVE_SESSIONS.get(player.getUuid()));
    }

    /**
     * Check if a player has an active session
     */
    public static boolean hasActiveSession(ServerPlayerEntity player) {
        return ACTIVE_SESSIONS.containsKey(player.getUuid());
    }

    /**
     * Add a waypoint at the player's current position
     */
    public void addWaypoint(Vec3d position) {
        waypoints.add(new NpcRegistry.Waypoint(position, defaultWaitTicks));
    }

    /**
     * Add a waypoint with custom wait time
     */
    public void addWaypoint(Vec3d position, int waitTicks) {
        waypoints.add(new NpcRegistry.Waypoint(position, waitTicks));
    }

    /**
     * Remove the last waypoint added
     */
    public boolean undoLastWaypoint() {
        if (waypoints.isEmpty()) return false;
        waypoints.remove(waypoints.size() - 1);
        return true;
    }

    /**
     * Set the default wait time for new waypoints
     */
    public void setDefaultWaitTicks(int ticks) {
        this.defaultWaitTicks = Math.max(0, ticks);
    }

    /**
     * Get the number of waypoints in this session
     */
    public int getWaypointCount() {
        return waypoints.size();
    }

    /**
     * Get the NPC ID being edited
     */
    public int getNpcId() {
        return npcId;
    }

    /**
     * Get all waypoints (read-only)
     */
    public List<NpcRegistry.Waypoint> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    /**
     * Save the path to the NPC and end the session
     */
    public void save() {
        if (!waypoints.isEmpty()) {
            NpcRegistry.setPath(npcId, new ArrayList<>(waypoints));
        }
        ACTIVE_SESSIONS.remove(playerUUID);
    }

    /**
     * Cancel the session without saving
     */
    public void cancel() {
        ACTIVE_SESSIONS.remove(playerUUID);
    }

    /**
     * Clear all sessions (useful for cleanup)
     */
    public static void clearAll() {
        ACTIVE_SESSIONS.clear();
    }
}
