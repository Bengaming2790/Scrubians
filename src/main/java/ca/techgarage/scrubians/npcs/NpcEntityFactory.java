package ca.techgarage.scrubians.npcs;

import com.mojang.authlib.GameProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NpcEntityFactory {

    /**
     * Creates a NEW player NPC with proper skin textures asynchronously.
     * This registers a NEW entry in the registry and gets a NEW ID.
     * USE THIS FOR: /npc create command
     */
    public static CompletableFuture<TrackingMannequinEntity> createPlayerNpc(
            ServerWorld world,
            Vec3d pos,
            String name,
            String skinName,
            boolean attackable
    ) {
        // Register NPC in registry FIRST and get unique ID
        int registryId = NpcRegistry.registerNpc(name, pos);

        // Save the skin to registry
        if (skinName != null && !skinName.equals(".")) {
            NpcRegistry.changeSkin(registryId, skinName);
        }

        System.out.println("[Scrubians] Creating NEW NPC #" + registryId + " (" + name + ") with skin: " + (skinName != null ? skinName : "default"));

        // Now create the entity with this ID
        return createNpcEntity(world, registryId, pos, name, skinName, attackable);
    }

    /**
     * Respawn an EXISTING NPC from registry data.
     * This REUSES the existing ID and does NOT create a new registry entry.
     * USE THIS FOR: /npc respawn {all/id} command
     */
    public static CompletableFuture<TrackingMannequinEntity> respawnNpcFromRegistry(
            ServerWorld world,
            int npcId
    ) {
        var npcDataOpt = NpcRegistry.getNpcById(npcId);
        if (npcDataOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("NPC #" + npcId + " not found in registry!"));
        }

        // Check if NPC already exists in world
        List<TrackingMannequinEntity> existing = new ArrayList<>();
        world.iterateEntities().forEach(entity -> {
            if (entity instanceof TrackingMannequinEntity npcEntity) {
                if (npcEntity.getNpcId() == npcId) {
                    existing.add(npcEntity);
                }
            }
        });

        if (!existing.isEmpty()) {
          //  System.out.println("[Scrubians] NPC #" + npcId + " already exists in world, skipping spawn");
            return CompletableFuture.completedFuture(existing.get(0));
        }

        NpcRegistry.NpcData npcData = npcDataOpt.get();
        Vec3d pos = npcData.getPosition();
        String skinName = npcData.skin != null ? npcData.skin : ".";

       // System.out.println("[Scrubians] Respawning EXISTING NPC #" + npcId + " (" + npcData.name + ") with skin: " + skinName + " - NO NEW REGISTRY ENTRY");

        // Create entity with EXISTING ID - does NOT call registerNpc()
        return createNpcEntity(world, npcId, pos, npcData.name, skinName, false);
    }

    /**
     * Internal method to create the actual NPC entity.
     * This does NOT touch the registry at all.
     */
    private static CompletableFuture<TrackingMannequinEntity> createNpcEntity(
            ServerWorld world,
            int npcId,
            Vec3d pos,
            String name,
            String skinName,
            boolean attackable
    ) {
        TrackingMannequinEntity npc = new TrackingMannequinEntity(EntityType.MANNEQUIN, world);

        // Set basic properties
        npc.setPos(pos.x, pos.y, pos.z);
        npc.setYaw(world.random.nextFloat() * 360f);
        npc.setCustomName(Text.literal(name));
        npc.setCustomNameVisible(true);
        npc.setNoGravity(false);
        npc.setInvulnerable(!attackable);
        npc.setNpcId(npcId); // Set the ID (either new or existing)

        MinecraftServer server = world.getServer();

        if (skinName == null || skinName.equals(".")) {
            // Default Steve skin
            npc.setComponent(DataComponentTypes.PROFILE, ProfileComponent.Static.EMPTY);
            world.spawnEntity(npc);
            return CompletableFuture.completedFuture(npc);
        }

        // Dynamic profile with skin
        ProfileComponent dynamicProfile = ProfileComponent.ofDynamic(skinName);

        return dynamicProfile.resolve(server.getApiServices().profileResolver())
                .thenApplyAsync(resolvedProfile -> {
                    npc.setComponent(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(resolvedProfile));
                    world.spawnEntity(npc);
                    return npc;
                }, server);
    }
}