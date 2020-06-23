package com.jacky8399.worstshop.shops;

import com.jacky8399.worstshop.shops.actions.ActionItemShop;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import com.jacky8399.worstshop.shops.wants.ShopWantsItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ItemShop {
    String owningShop;
    ActionItemShop shop;
    Condition condition;
    public ItemShop(ActionItemShop orig, Condition condition) {
        this.shop = orig;
        this.condition = condition != null ? condition : ConditionConstant.TRUE;
        this.owningShop = ShopManager.currentShopId;
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
        return ShopManager.getShop(owningShop).map(owner -> owner.canPlayerView(player)).orElse(false) &&
                (condition == null || condition.test(player));
    }

    public boolean isSellable(ItemStack stack, Player player) {
        return acceptsSelling() && getSellAmount(player, stack) > 0;
    }

    public boolean isSellable(Inventory inventory, Player player) {
        return acceptsSelling() && getSellAmount(player, inventory) > 0;
    }

    public int getSellAmount(Player player, Inventory inventory) {
        return Math.min(shop.buildSellShop(player).getPlayerMaxPurchase(player), getSellCost(player).getMaximumMultiplier(inventory));
    }
    public int getSellAmount(Player player, ItemStack stack) {
        return Math.min(shop.buildSellShop(player).getPlayerMaxPurchase(player), getSellCost(player).getMaximumMultiplier(stack));
    }

    public void sell(ItemStack stack, Player player) {
        shop.doSellTransaction(player, stack, getSellAmount(player, stack));
    }

    public void sellAll(Inventory inventory, Player player) {
        shop.doSellTransaction(player, inventory, getSellAmount(player, inventory));
    }
}
