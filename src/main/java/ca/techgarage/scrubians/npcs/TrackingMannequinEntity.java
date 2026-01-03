package ca.techgarage.scrubians.npcs;

import ca.techgarage.scrubians.Scrubians;
import ca.techgarage.scrubians.dialogue.DialoguePackets;
import ca.techgarage.scrubians.dialogue.DialogueSessionManager;
import ca.techgarage.scrubians.dialogue.NPCDialogue;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrackingMannequinEntity extends MannequinEntity {

    private static final double LOOK_RANGE = (double) Scrubians.CONFIG.NPCLookDistance;
    private static final float MAX_HEAD_ROTATION = 75.0F;
    private static final float ROTATION_SPEED = 10.0F;
    private static final String NPC_ID_KEY = "npc_id";

    // Interaction cooldown tracking
    private static final Map<UUID, Long> LAST_INTERACTION = new HashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 2000; // 2 seconds

    // Path following constants
    private static final double WAYPOINT_REACH_DISTANCE = 0.5;
    private static final double HORIZONTAL_SPEED = 0.1;
    private static final double JUMP_VELOCITY = 0.42;

    private int lookTimer = 0;
    private float targetYaw = 0.0F;
    private float targetPitch = 0.0F;
    private int npcId;

    // Path following state
    private int currentWaypointIndex = 0;
    private int waypointWaitTimer = 0;
    private boolean isWaitingAtWaypoint = false;

    public TrackingMannequinEntity(EntityType<MannequinEntity> entityType, World world) {
        super(entityType, world);
    }

    public void setNpcId(int id) {
        this.npcId = id;
    }

    public int getNpcId() {
        return this.npcId;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getEntityWorld().isClient()) {
            // Verify NPC still exists in registry every 200 ticks (10 seconds)
            if (this.age % 200 == 0 && this.npcId >= 0) {
                var npcOpt = NpcRegistry.getNpcById(this.npcId);
                if (npcOpt.isEmpty()) {
                    System.err.println("[Scrubians] NPC #" + this.npcId + " no longer in registry, removing entity");
                    this.discard();
                    return;
                }
            }

            if (this.age <= 3 && this.npcId >= 0) {
                var npcOpt = NpcRegistry.getNpcById(this.npcId);
                if (npcOpt.isPresent()) {
                    var pathSize = npcOpt.get().getPath().size();
                    if (pathSize > 0 && this.age == 1) {
                        System.out.println("[Scrubians] NPC #" + this.npcId + " has " + pathSize + " waypoints, starting pathfinding");
                    }
                }
            }

            this.updateLookDirection();
            this.updatePathFollowing();

            if (this.age % 10 == 0) {
                NpcRegistry.getNpcById(this.npcId).ifPresent(npc -> {
                    NpcRegistry.changePosition(this.npcId, this.getEntityPos());
                });
            }
        }
    }
    private void initializePathfinding() {
        this.currentWaypointIndex = 0;
        this.waypointWaitTimer = 0;
        this.isWaitingAtWaypoint = false;

        NpcRegistry.getNpcById(this.npcId).ifPresent(npc -> {
            if (npc.getPath() != null && !npc.getPath().isEmpty()) {
                System.out.println("[Scrubians] NPC #" + this.npcId + " initialized with " + npc.getPath().size() + " waypoints");
            }
        });
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putInt(NPC_ID_KEY, this.npcId);
        view.putInt("currentWaypoint", this.currentWaypointIndex);
        view.putInt("waypointWaitTimer", this.waypointWaitTimer);
        view.putBoolean("isWaiting", this.isWaitingAtWaypoint);
        view.putBoolean("pathfindingInitialized", true);
       // System.out.println("[Scrubians] WRITE NBT - Saving NPC ID: " + this.npcId + " at " + this.getEntityPos());
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.npcId = view.getInt(NPC_ID_KEY, -1);
        this.currentWaypointIndex = view.getInt("currentWaypoint", 0);
        this.waypointWaitTimer = view.getInt("waypointWaitTimer", 0);
        this.isWaitingAtWaypoint = view.getBoolean("isWaiting", false);

        System.out.println("[Scrubians] READ NBT - Loaded NPC ID: " + this.npcId + " at " + this.getEntityPos());

        // CRITICAL: Remove orphaned mannequins (those without valid NPC data)
        if (this.npcId < 0) {
            System.err.println("[Scrubians] ✗ Removing mannequin with invalid NPC ID at " + this.getEntityPos());
            this.discard();
            return;
        }

        // Check if NPC exists in registry
        var npcOpt = NpcRegistry.getNpcById(this.npcId);
        if (npcOpt.isEmpty()) {
            System.err.println("[Scrubians] ✗ WARNING: NPC #" + this.npcId + " NOT FOUND in registry! Removing orphaned entity at " + this.getEntityPos());
            this.discard();
            return;
        }

        System.out.println("[Scrubians] ✓ NPC #" + this.npcId + " found in registry: " + npcOpt.get().name);
    }

    @Override
    protected boolean isImmobile() {
        return false;
    }

    private void updatePathFollowing() {
        var npcDataOpt = NpcRegistry.getNpcById(this.npcId);
        if (npcDataOpt.isEmpty()) return;

        List<NpcRegistry.Waypoint> path = npcDataOpt.get().getPath();
        if (path == null || path.isEmpty()) return;

        if (isWaitingAtWaypoint) {
            waypointWaitTimer--;
            if (waypointWaitTimer <= 0) {
                isWaitingAtWaypoint = false;
                currentWaypointIndex = (currentWaypointIndex + 1) % path.size();
            }
            Vec3d currentVel = this.getVelocity();
            this.setVelocity(0, currentVel.y, 0);
            return;
        }

        NpcRegistry.Waypoint targetWaypoint = path.get(currentWaypointIndex);
        Vec3d targetPos = targetWaypoint.toVec3d();
        Vec3d currentPos = this.getEntityPos();

        double deltaX = targetPos.x - currentPos.x;
        double deltaZ = targetPos.z - currentPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        double verticalDiff = Math.abs(targetPos.y - currentPos.y);
        if (horizontalDistance < WAYPOINT_REACH_DISTANCE && verticalDiff < WAYPOINT_REACH_DISTANCE) {
            isWaitingAtWaypoint = true;
            waypointWaitTimer = targetWaypoint.waitTicks;
            Vec3d currentVel = this.getVelocity();
            this.setVelocity(0, currentVel.y, 0);
            return;
        }

        Vec3d horizontalDirection = new Vec3d(deltaX, 0, deltaZ).normalize();
        Vec3d currentVel = this.getVelocity();

        double velX = horizontalDirection.x * HORIZONTAL_SPEED;
        double velZ = horizontalDirection.z * HORIZONTAL_SPEED;
        double velY = currentVel.y;

        if (targetPos.y > currentPos.y + 0.5 && this.isOnGround()) {
            velY = JUMP_VELOCITY;
        }

        float targetBodyYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        this.bodyYaw = targetBodyYaw;
        this.setYaw(targetBodyYaw);

        this.setVelocity(velX, velY, velZ);
    }

    private void updateLookDirection() {
        if (this.age % 10 == 0) {
            PlayerEntity closestPlayer = this.findClosestPlayer();

            if (closestPlayer != null) {
                this.lookTimer = 40;
                calculateLookAngles(closestPlayer);
            } else if (this.lookTimer <= 0) {
                this.targetYaw = this.bodyYaw;
                this.targetPitch = 0.0F;
            }
        }

        if (this.lookTimer > 0) {
            this.lookTimer--;
        }

        this.headYaw = this.approachRotation(this.headYaw, this.targetYaw, ROTATION_SPEED);
        this.setPitch(this.approachRotation(this.getPitch(), this.targetPitch, ROTATION_SPEED));
    }

    private PlayerEntity findClosestPlayer() {
        Box searchBox = this.getBoundingBox().expand(LOOK_RANGE);
        List<PlayerEntity> players = this.getEntityWorld().getEntitiesByClass(
                PlayerEntity.class,
                searchBox,
                player -> !player.isSpectator() && player.isAlive()
        );

        if (players.isEmpty()) return null;

        PlayerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (PlayerEntity player : players) {
            double distance = this.squaredDistanceTo(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = player;
            }
        }

        return closest;
    }

    private void calculateLookAngles(PlayerEntity player) {
        Vec3d npcPos = this.getEntityPos().add(0, this.getStandingEyeHeight(), 0);
        Vec3d playerPos = player.getEntityPos().add(0, player.getStandingEyeHeight(), 0);

        double deltaX = playerPos.x - npcPos.x;
        double deltaY = playerPos.y - npcPos.y;
        double deltaZ = playerPos.z - npcPos.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        float yawDifference = MathHelper.wrapDegrees(yaw - this.bodyYaw);
        yawDifference = MathHelper.clamp(yawDifference, -MAX_HEAD_ROTATION, MAX_HEAD_ROTATION);
        this.targetYaw = this.bodyYaw + yawDifference;

        this.targetPitch = (float) -(Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        this.targetPitch = MathHelper.clamp(this.targetPitch, -75.0F, 75.0F);
    }

    private float approachRotation(float current, float target, float speed) {
        float difference = MathHelper.wrapDegrees(target - current);
        if (Math.abs(difference) <= speed) return target;
        return current + Math.signum(difference) * speed;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!this.getEntityWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            // Check cooldown
            long currentTime = System.currentTimeMillis();
            Long lastTime = LAST_INTERACTION.get(player.getUuid());

            if (lastTime != null && (currentTime - lastTime) < INTERACTION_COOLDOWN_MS) {
                // Still on cooldown, ignore interaction
                return ActionResult.SUCCESS;
            }

            // Update last interaction time
            LAST_INTERACTION.put(player.getUuid(), currentTime);

            // Load dialogue from registry
            NPCDialogue dialogue = createDialogue();

            if (dialogue == null || dialogue.getPages().isEmpty()) {
                player.sendMessage(net.minecraft.text.Text.literal("§7*" + this.getCustomName().getString() + " has nothing to say*"), false);
                return ActionResult.SUCCESS;
            }

            // Start dialogue session (this handles sending internally)
            DialogueSessionManager.startDialogue(serverPlayer, this.npcId, dialogue);

            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    protected NPCDialogue createDialogue() {
        var npcDataOpt = NpcRegistry.getNpcById(this.npcId);
        if (npcDataOpt.isEmpty()) {
            return null;
        }

        var npcData = npcDataOpt.get();
        String name = npcData.name != null ? npcData.name : "NPC";

        NpcRegistry.DialogueData dialogueData = npcData.getDialogue();
        if (dialogueData == null || dialogueData.pages == null || dialogueData.pages.isEmpty()) {
            return null;
        }

        NPCDialogue dialogue = new NPCDialogue(name);

        for (NpcRegistry.DialogueData.DialoguePageData pageData : dialogueData.pages) {
            if (pageData.options == null || pageData.options.isEmpty()) {
                dialogue.addPage(pageData.text);
            } else {
                NPCDialogue.DialogueOption[] options = pageData.options.stream()
                        .map(opt -> new NPCDialogue.DialogueOption(opt.text, opt.action))
                        .toArray(NPCDialogue.DialogueOption[]::new);

                dialogue.addPageWithOptions(pageData.text, options);
            }
        }

        return dialogue;
    }
}