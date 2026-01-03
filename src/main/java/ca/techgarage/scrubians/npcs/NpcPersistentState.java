// java
package ca.techgarage.scrubians.npcs;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NpcPersistentState extends PersistentState {

    public static final String ID = "scrubians_npcs";

    public final List<NpcRegistry.NpcData> npcs = new ArrayList<>();

    public NpcPersistentState() { }

    // Constructor used by PersistentStateManager factory (reads existing data)
    public NpcPersistentState(NbtCompound nbt) {
        readNbt(nbt);
    }

    /** Get or create the persistent state for a world */
    public static NpcPersistentState get(ServerWorld world) {
        Object mgr = world.getPersistentStateManager();

        // Try common getOrCreate(Function, String) signature
        try {
            Method m = mgr.getClass().getMethod("getOrCreate", java.util.function.Function.class, String.class);
            java.util.function.Function<NbtCompound, NpcPersistentState> fn = NpcPersistentState::new;
            Object res = m.invoke(mgr, fn, ID);
            if (res instanceof NpcPersistentState) return (NpcPersistentState) res;
        } catch (Throwable ignored) { }

        // Try getOrCreate(String)
        try {
            Method m = mgr.getClass().getMethod("getOrCreate", String.class);
            Object res = m.invoke(mgr, ID);
            if (res instanceof NpcPersistentState) return (NpcPersistentState) res;
        } catch (Throwable ignored) { }

        // Try get(String)
        try {
            Method m = mgr.getClass().getMethod("get", String.class);
            Object res = m.invoke(mgr, ID);
            if (res instanceof NpcPersistentState) return (NpcPersistentState) res;
        } catch (Throwable ignored) { }

        // As a last resort, construct a new instance and attempt to register it
        NpcPersistentState state = new NpcPersistentState();
        try {
            Method m = mgr.getClass().getMethod("set", String.class, PersistentState.class);
            m.invoke(mgr, ID, state);
        } catch (Throwable ignored) { }

        return state;
    }

    /** Load from NBT (called by PersistentStateManager automatically) */
    // removed @Override because signature may differ in mappings
    public void readNbt(NbtCompound nbt) {
        npcs.clear();
        Optional<NbtList> listOpt = nbt.getList("npcs");
        NbtList list = listOpt.orElse(new NbtList());

        for (int i = 0; i < list.size(); i++) {
            Optional<NbtCompound> tagOpt = list.getCompound(i);
            if (!tagOpt.isPresent()) continue;
            NbtCompound tag = tagOpt.get();

            int id = tag.getInt("id").orElse(0);
            String name = tag.getString("name").orElse("");
            double x = tag.getDouble("x").orElse(0.0);
            double y = tag.getDouble("y").orElse(0.0);
            double z = tag.getDouble("z").orElse(0.0);

            npcs.add(new NpcRegistry.NpcData(id, name.isEmpty() ? null : name, new Vec3d(x, y, z)));
        }
    }

    // removed @Override because signature may differ in mappings
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (NpcRegistry.NpcData npc : npcs) {
            NbtCompound tag = new NbtCompound();
            tag.putInt("id", npc.id);
            tag.putString("name", npc.name == null ? "" : npc.name);
            tag.putDouble("x", npc.getPosition().x);
            tag.putDouble("y", npc.getPosition().y);
            tag.putDouble("z", npc.getPosition().z);
            list.add(tag);
        }
        nbt.put("npcs", list);
        return nbt;
    }
}