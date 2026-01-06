package ca.techgarage.scrubians;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The type Scrubians permissions.
 */
public final class ScrubiansPermissions {

    private ScrubiansPermissions() {}
    public static boolean has(ServerCommandSource source, String permission) {
        // Console always allowed
        if (source.getEntity() == null) return true;

        // OP fallback
        if (source.hasPermissionLevel(2)) return true;

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return false;
        }

        try {
            LuckPerms lp = LuckPermsProvider.get();
            return lp.getUserManager()
                    .getUser(player.getUuid())
                    .getCachedData()
                    .getPermissionData()
                    .checkPermission(permission)
                    .asBoolean();
        } catch (IllegalStateException e) {
            // LuckPerms not installed
            return false;
        }
    }
}