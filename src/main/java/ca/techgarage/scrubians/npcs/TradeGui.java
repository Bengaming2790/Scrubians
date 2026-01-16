package ca.techgarage.scrubians.npcs;

import eu.pb4.sgui.api.gui.MerchantGui;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

import java.util.Optional;

/**
 * Player-facing trading GUI (villager-style)
 */
public class TradeGui {

    public static void open(ServerPlayerEntity player, int npcId) {
        var npcOpt = NpcRegistry.getNpcById(npcId);
        if (npcOpt.isEmpty()) {
            player.sendMessage(Text.literal("§cNPC not found!"), false);
            return;
        }

        var npcData = npcOpt.get();
        TradeData tradeData = npcData.getTradeData();

        if (tradeData == null || tradeData.trades.isEmpty()) {
            player.sendMessage(Text.literal("§7*" + npcData.name + " has nothing to trade*"), false);
            return;
        }

        // Create merchant GUI
        MerchantGui gui = new MerchantGui(player, false);
        gui.setTitle(Text.literal("Trading with " + npcData.name));

        // Convert our trades to Minecraft trade offers and add them
        for (TradeData.Trade trade : tradeData.trades) {
            if (!trade.isValid()) continue;

            // Create TradedItem for costs
            TradedItem firstBuyItem = new TradedItem(trade.firstCost.getItem(), trade.firstCost.getCount());
            Optional<TradedItem> secondBuyItem = trade.secondCost.isEmpty() ?
                    Optional.empty() :
                    Optional.of(new TradedItem(trade.secondCost.getItem(), trade.secondCost.getCount()));

            // Create trade offer
            TradeOffer offer = new TradeOffer(
                    firstBuyItem,
                    secondBuyItem,
                    trade.result.copy(),
                    trade.uses,
                    trade.maxUses,
                    trade.experience,
                    0.05f  // priceMultiplier (standard villager value)
            );

            gui.addTrade(offer);
        }

        // Override onTrade to track usage
        MerchantGui trackingGui = new MerchantGui(player, false) {
            @Override
            public boolean onTrade(TradeOffer offer) {
                // Find the matching trade and increment usage
                int offerIndex = getOfferIndex(offer);
                if (offerIndex >= 0 && offerIndex < tradeData.trades.size()) {
                    TradeData.Trade trade = tradeData.trades.get(offerIndex);
                    trade.uses++;

                    // Save updated trade data
                    NpcRegistry.setTradeData(npcId, tradeData);
                }

                return super.onTrade(offer);
            }
        };

        trackingGui.setTitle(Text.literal(npcData.name));

        // Add trades to tracking GUI
        for (TradeData.Trade trade : tradeData.trades) {
            if (!trade.isValid()) continue;

            TradedItem firstBuyItem = new TradedItem(trade.firstCost.getItem(), trade.firstCost.getCount());
            Optional<TradedItem> secondBuyItem = trade.secondCost.isEmpty() ?
                    Optional.empty() :
                    Optional.of(new TradedItem(trade.secondCost.getItem(), trade.secondCost.getCount()));

            TradeOffer offer = new TradeOffer(
                    firstBuyItem,
                    secondBuyItem,
                    trade.result.copy(),
                    trade.uses,
                    trade.maxUses,
                    trade.experience,
                    0.05f
            );

            trackingGui.addTrade(offer);
        }

        trackingGui.open();
    }
}