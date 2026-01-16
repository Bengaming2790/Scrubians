package ca.techgarage.scrubians.npcs;

import com.google.gson.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.StringNbtReader;
/**
 * Represents trading data for an NPC
 */
public class TradeData {

    public static class Trade {
        public ItemStack result;      // What the NPC is selling (X)
        public ItemStack firstCost;   // First buy slot (O1)
        public ItemStack secondCost;  // Second buy slot (O2) - can be empty
        public int maxUses;           // How many times this trade can be used
        public int uses;              // Current usage count
        public int experience;        // XP given to player

        public Trade(ItemStack result, ItemStack firstCost, ItemStack secondCost, int maxUses, int experience) {
            this.result = result;
            this.firstCost = firstCost;
            this.secondCost = secondCost;
            if (maxUses == 999) {
                maxUses = Integer.MAX_VALUE;
            } else {
                this.maxUses = maxUses;
            }
            this.uses = 0;
            this.experience = experience;
        }

        public Trade() {
            this(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 999, 0);
        }

        public boolean isValid() {
            return !result.isEmpty() && !firstCost.isEmpty();
        }
    }

    public List<Trade> trades;

    public TradeData() {
        this.trades = new ArrayList<>();
    }

    // Gson serializer for ItemStack
    public static class ItemStackSerializer implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

        @Override
        public JsonElement serialize(ItemStack stack, Type typeOfSrc, JsonSerializationContext context) {
            if (stack.isEmpty()) {
                return JsonNull.INSTANCE;
            }

            JsonObject json = new JsonObject();
            json.addProperty("id", stack.getItem().toString());
            json.addProperty("count", stack.getCount());

            // Serialize components to NBT, then to JSON
            NbtCompound nbt = new NbtCompound();
            NbtCompound stackNbt = (NbtCompound) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                    .getOrThrow();

            // Store the full NBT data
            json.addProperty("nbt", stackNbt.toString());

            return json;
        }

        @Override
        public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonNull()) {
                return ItemStack.EMPTY;
            }

            JsonObject obj = json.getAsJsonObject();

            if (obj.has("nbt")) {
                try {
                    String nbtString = obj.get("nbt").getAsString();
                    // Change this line:
                    NbtCompound nbt = StringNbtReader.readCompound(nbtString);
                    return ItemStack.CODEC.decode(NbtOps.INSTANCE, nbt)
                            .getOrThrow().getFirst();
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            }

            return ItemStack.EMPTY;
        }
    }

    // Gson serializer for Trade
    public static class TradeSerializer implements JsonSerializer<Trade>, JsonDeserializer<Trade> {
        private final ItemStackSerializer itemStackSerializer = new ItemStackSerializer();

        @Override
        public JsonElement serialize(Trade trade, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();

            json.add("result", itemStackSerializer.serialize(trade.result, ItemStack.class, context));
            json.add("firstCost", itemStackSerializer.serialize(trade.firstCost, ItemStack.class, context));
            json.add("secondCost", itemStackSerializer.serialize(trade.secondCost, ItemStack.class, context));
            json.addProperty("maxUses", trade.maxUses);
            json.addProperty("uses", trade.uses);
            json.addProperty("experience", trade.experience);

            return json;
        }

        @Override
        public Trade deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            Trade trade = new Trade();

            trade.result = itemStackSerializer.deserialize(obj.get("result"), ItemStack.class, context);
            trade.firstCost = itemStackSerializer.deserialize(obj.get("firstCost"), ItemStack.class, context);
            trade.secondCost = itemStackSerializer.deserialize(obj.get("secondCost"), ItemStack.class, context);
            trade.maxUses = obj.get("maxUses").getAsInt();
            trade.uses = obj.get("uses").getAsInt();
            trade.experience = obj.get("experience").getAsInt();

            return trade;
        }
    }
}