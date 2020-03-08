package com.jacky8399.worstshop.shops;

import com.jacky8399.worstshop.shops.actions.ActionItemShop;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import com.jacky8399.worstshop.shops.wants.ShopWantsItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ItemShop {

    String owningShop;
    ActionItemShop shop;
    String optionalPermission;
    public ItemShop(ActionItemShop orig, String optionalPermission) {
        this.shop = orig;
        this.optionalPermission = optionalPermission;
        this.owningShop = ShopManager.currentShop;
    }

    private ShopWantsItem getSellCost(Player player) {
        return ((ShopWantsItem) shop.buildSellShop(player).cost);
    }

    private ShopWants getSellReward(Player player) {
        return shop.buildSellShop(player).reward;
    }

    private ShopWantsItem getBuyReward(Player player) {
        return ((ShopWantsItem) shop.buildBuyShop(player).reward);
    }

    private ShopWants getBuyCost(Player player) {
        return shop.buildBuyShop(player).cost;
    }

    public boolean acceptsSelling() {
        return shop.sellPrice > 0;
    }
    public boolean acceptsBuying() {
        return shop.buyPrice > 0;
    }

    public boolean isAvailableTo(Player player) {
        return ShopManager.checkPerms(player, owningShop) && (optionalPermission == null ||
                player.hasPermission(optionalPermission));
    }

    public boolean isSellable(ItemStack stack, Player player) {
        return acceptsSelling() && getSellCost(player).canAfford(stack);
    }

    public boolean isSellable(Inventory inventory, Player player) {
        return acceptsSelling() && getSellCost(player).canAfford(inventory);
    }

    public void sell(ItemStack stack, Player player) {
        ShopWantsItem sellCost = getSellCost(player);
        // find max sellable
        int multiplier = sellCost.getMaximumMultiplier(stack);
        if (multiplier > 0) {
            ((ShopWantsItem) sellCost.multiply(multiplier)).deduct(stack);
            double refund = getSellReward(player).multiply(multiplier).grantOrRefund(player);
            if (refund > 0) {
                sellCost.multiply(refund).grantOrRefund(player);
            }
        }
        // send message
        player.sendMessage(shop.buildSellShop(player).formatPurchaseMessage(player, multiplier));
    }

    public void sellAll(Inventory inventory, Player player) {
        shop.doSellTransaction(player, inventory, getSellCost(player).getMaximumMultiplier(inventory));
    }

    public void sellAll(Player player) {
        shop.doSellTransaction(player, getSellCost(player).getMaximumMultiplier(player));
    }

    public boolean hasSellableItems(Player player) {
        return isSellable(player.getInventory(), player);
    }
}
