package ca.techgarage.scrubians.npcs;

import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin GUI for editing NPC trades
 * Layout (chest 9x6):
 * Each row represents one trade:
 * [X][O2][O1] [Info] ... [Delete] [Next Page] [Save & Close]
 */
public class TradeEditorGui extends SimpleGui {

    private final int npcId;
    private final List<TradeData.Trade> trades;
    private int currentPage = 0;
    private static final int TRADES_PER_PAGE = 5;

    public TradeEditorGui(ServerPlayerEntity player, int npcId) {
        super(ScreenHandlerType.GENERIC_9X6, player, false);
        this.npcId = npcId;

        // Load existing trades from registry
        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isPresent() && npcOpt.get().getTradeData() != null) {
            this.trades = new ArrayList<>(npcOpt.get().getTradeData().trades);
        } else {
            this.trades = new ArrayList<>();
        }

        // Ensure we have at least 5 empty trade slots
        while (trades.size() < 5) {
            trades.add(new TradeData.Trade());
        }

        this.setTitle(Text.literal("Trade Editor - NPC #" + npcId));
        this.updateDisplay();
    }

    private void updateDisplay() {
        // Clear GUI
        for (int i = 0; i < this.getSize(); i++) {
            this.clearSlot(i);
        }

        int startIndex = currentPage * TRADES_PER_PAGE;

        // Display trades
        for (int i = 0; i < TRADES_PER_PAGE && (startIndex + i) < trades.size(); i++) {
            int tradeIndex = startIndex + i;
            TradeData.Trade trade = trades.get(tradeIndex);
            int row = i;

            // X - Result (what NPC sells)
            this.setSlot(row * 9, trade.result.isEmpty() ?
                            createPlaceholder("Result", Formatting.GREEN) : trade.result,
                    createTradeSlotCallback(tradeIndex, 0));

            // O2 - Second cost (optional)
            this.setSlot(row * 9 + 1, trade.secondCost.isEmpty() ?
                            createPlaceholder("Cost 2 (Optional)", Formatting.YELLOW) : trade.secondCost,
                    createTradeSlotCallback(tradeIndex, 1));

            // O1 - First cost (required)
            this.setSlot(row * 9 + 2, trade.firstCost.isEmpty() ?
                            createPlaceholder("Cost 1 (Required)", Formatting.GOLD) : trade.firstCost,
                    createTradeSlotCallback(tradeIndex, 2));

            // Info button
            ItemStack infoItem = new ItemStack(Items.BOOK);
            infoItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Trade Info").formatted(Formatting.AQUA));

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Max Uses: " + trade.maxUses).formatted(Formatting.GRAY));
            lore.add(Text.literal("Experience: " + trade.experience).formatted(Formatting.GRAY));
            lore.add(Text.empty());
            lore.add(Text.literal("Click to edit settings").formatted(Formatting.YELLOW));

            infoItem.set(net.minecraft.component.DataComponentTypes.LORE,
                    new net.minecraft.component.type.LoreComponent(lore));

            this.setSlot(row * 9 + 3, infoItem, (index, type, action, gui) -> {
                openTradeSettings(tradeIndex);
            });

            // Delete button
            ItemStack deleteItem = new ItemStack(Items.BARRIER);
            deleteItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Delete Trade").formatted(Formatting.RED));

            this.setSlot(row * 9 + 7, deleteItem, (index, type, action, gui) -> {
                trade.result = ItemStack.EMPTY;
                trade.firstCost = ItemStack.EMPTY;
                trade.secondCost = ItemStack.EMPTY;
                updateDisplay();
            });
        }

        // Navigation and control buttons (bottom row)
        int bottomRow = 5 * 9;

        // Previous page
        if (currentPage > 0) {
            ItemStack prevItem = new ItemStack(Items.ARROW);
            prevItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Previous Page").formatted(Formatting.YELLOW));
            this.setSlot(bottomRow, prevItem, (index, type, action, gui) -> {
                currentPage--;
                updateDisplay();
            });
        }

        // Next page / Add more trades
        ItemStack nextItem = new ItemStack(Items.ARROW);
        nextItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("Next Page / Add More").formatted(Formatting.YELLOW));
        this.setSlot(bottomRow + 1, nextItem, (index, type, action, gui) -> {
            if ((currentPage + 1) * TRADES_PER_PAGE >= trades.size()) {
                // Add 5 more empty trade slots
                for (int j = 0; j < 5; j++) {
                    trades.add(new TradeData.Trade());
                }
            }
            currentPage++;
            updateDisplay();
        });

        // Save & Close
        ItemStack saveItem = new ItemStack(Items.EMERALD);
        saveItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("Save & Close").formatted(Formatting.GREEN));

        List<Text> saveLore = new ArrayList<>();
        saveLore.add(Text.literal("Saves all trades and closes").formatted(Formatting.GRAY));
        saveLore.add(Text.literal("the editor").formatted(Formatting.GRAY));
        saveItem.set(net.minecraft.component.DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(saveLore));

        this.setSlot(bottomRow + 8, saveItem, (index, type, action, gui) -> {
            saveAndClose();
        });
    }

    private eu.pb4.sgui.api.elements.GuiElementInterface.ItemClickCallback createTradeSlotCallback(int tradeIndex, int slotType) {
        // slotType: 0 = result, 1 = secondCost, 2 = firstCost
        return (index, clickType, actionType) -> {
            if (tradeIndex >= trades.size()) return;

            TradeData.Trade trade = trades.get(tradeIndex);
            ItemStack cursor = getPlayer().currentScreenHandler.getCursorStack();

            // Handle item placement/removal based on action type
            if (actionType == net.minecraft.screen.slot.SlotActionType.PICKUP) {
                if (!cursor.isEmpty()) {
                    // Place item from cursor
                    switch (slotType) {
                        case 0 -> trade.result = cursor.copy();
                        case 1 -> trade.secondCost = cursor.copy();
                        case 2 -> trade.firstCost = cursor.copy();
                    }
                    getPlayer().currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                } else {
                    // Take item to cursor
                    ItemStack toTake = switch (slotType) {
                        case 0 -> trade.result;
                        case 1 -> trade.secondCost;
                        case 2 -> trade.firstCost;
                        default -> ItemStack.EMPTY;
                    };

                    if (!toTake.isEmpty()) {
                        getPlayer().currentScreenHandler.setCursorStack(toTake.copy());
                        switch (slotType) {
                            case 0 -> trade.result = ItemStack.EMPTY;
                            case 1 -> trade.secondCost = ItemStack.EMPTY;
                            case 2 -> trade.firstCost = ItemStack.EMPTY;
                        }
                    }
                }

                updateDisplay();
            }
        };
    }

    private void openTradeSettings(int tradeIndex) {
        getPlayer().sendMessage(Text.literal("Trade settings GUI coming soon! Trade #" + tradeIndex), false);
    }

    private void saveAndClose() {
        // Remove empty trades
        trades.removeIf(trade -> !trade.isValid());

        // Create trade data
        TradeData tradeData = new TradeData();
        tradeData.trades = new ArrayList<>(trades);

        // Save to registry
        NpcRegistry.setTradeData(npcId, tradeData);

        getPlayer().sendMessage(
                Text.literal("Saved " + trades.size() + " trades for NPC #" + npcId).formatted(Formatting.GREEN),
                false
        );

        this.close();
    }

    private ItemStack createPlaceholder(String name, Formatting color) {
        ItemStack placeholder = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        placeholder.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(name).formatted(color));
        return placeholder;
    }
}