package com.jacky8399.worstshop.shops;

import com.jacky8399.worstshop.shops.actions.ActionItemShop;
import com.jacky8399.worstshop.shops.commodity.CommodityItem;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemShop {
    public final ShopReference owningShop;
    public ActionItemShop shop;
    public Condition condition;
    public ItemShop(ActionItemShop orig, Condition condition) {
        this.shop = orig;
        this.condition = condition != null ? condition : ConditionConstant.TRUE;
        this.owningShop = ShopReference.of(ParseContext.findLatest(Shop.class));
    }

    private CommodityItem getSellCost(Player player) {
        return ((CommodityItem) shop.buildSellShop(player).cost);
    }

    private Commodity getSellReward(Player player) {
        return shop.buildSellShop(player).reward;
    }

    private Commodity getBuyCost(Player player) {
        return shop.buildBuyShop(player).cost;
    }

    private CommodityItem getBuyReward(Player player) {
        return ((CommodityItem) shop.buildBuyShop(player).reward);
    }


    public boolean acceptsSelling() {
        return shop.sellPrice > 0;
    }
    public boolean acceptsBuying() {
        return shop.buyPrice > 0;
    }

    public boolean isAvailableTo(Player player) {
        return (owningShop.find().map(shop -> shop.canPlayerView(player)).orElse(true)) &&
                (condition == null || condition.test(player));
    }

    public boolean isSellable(ItemStack stack, Player player) {
        return acceptsSelling() && getSellAmount(player, stack) > 0;
    }

    public int getSellAmount(Player player, ItemStack stack) {
        return Math.min(shop.buildSellShop(player).getPlayerMaxPurchase(player), getSellCost(player).getMaximumMultiplier(stack));
    }

    public void sell(ItemStack stack, Player player) {
        shop.doSellTransaction(player, stack, getSellAmount(player, stack));
    }
}
