package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.ItemShop;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import com.jacky8399.worstshop.shops.wants.ShopWantsItem;
import com.jacky8399.worstshop.shops.wants.ShopWantsMoney;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class ActionItemShop extends ShopAction implements IParentElementReader {

    boolean isStackDynamic = false;
    ShopElement parentElement;
    ActionShop buy;
    ActionShop sell;
    boolean sellAll;
    public double buyPrice = 0, sellPrice = 0;

    // shortcut
    public ActionItemShop(String input, Map<String, Object> yamlParent) {
        super(null);
        String[] prices = input.split("\\s|,");
        buyPrice = Double.parseDouble(prices[0].trim());
        sellPrice = Double.parseDouble(prices[1].trim());

        sellAll = sellPrice != 0;
    }

    public ActionItemShop(Map<String, Object> yaml, Map<String, Object> yamlParent) {
        super(yaml);
        if (yaml.containsKey("buyPrice")) {
            buyPrice = ((Number) yaml.get("buyPrice")).doubleValue();
        }
        if (yaml.containsKey("sellPrice")) {
            sellPrice = ((Number) yaml.get("sellPrice")).doubleValue();
        }

        // shortcut
        if (yaml.containsKey("prices")) {
            String[] prices = ((String) yaml.get("prices")).split("\\s|,");
            buyPrice = Double.parseDouble(prices[0].trim());
            sellPrice = Double.parseDouble(prices[1].trim());
        }

        sellAll = (boolean) yaml.getOrDefault("allowSellAll", true);
    }

    public ShopWants buildWants(Player player) {
        // try to get static stack
        if (!isStackDynamic) {
            return new ShopWantsItem(((StaticShopElement) parentElement).stack);
        }
        return new ShopWantsItem(parentElement.createStack(player));
    }

    public ActionShop buildBuyShop(ShopWants item) {
        return buyPrice > 0 ? new ActionShop(new ShopWantsMoney(buyPrice), item) : null;
    }

    public ActionShop buildBuyShop(Player player) {
        return buildBuyShop(buildWants(player));
    }

    public ActionShop buildSellShop(ShopWants item) {
        return sellPrice > 0 ? new ActionShop(item, new ShopWantsMoney(sellPrice)) : null;
    }

    public ActionShop buildSellShop(Player player) {
        return buildSellShop(buildWants(player));
    }

    public void doBuyTransaction(Player player, double count) {
        if (count == 0) {
            player.sendMessage(ActionShop.formatNothingMessage());
            return;
        }
        ActionShop shop = buildBuyShop(player);
        ShopWants multipliedCost = shop.cost.multiply(count);
        if (multipliedCost.canAfford(player)) {
            multipliedCost.deduct(player);
            double refund = shop.reward.multiply(count).grantOrRefund(player);
            if (refund > 0) {
                // refunds DO NOT allow refunding
                shop.cost.multiply(refund).grantOrRefund(player);
                player.sendMessage(shop.formatRefundMessage(player, refund));
            }
            player.sendMessage(shop.formatPurchaseMessage(player, count - refund));
        } else {
            player.sendMessage(ActionShop.formatNothingMessage());
        }
    }

    public void doSellTransaction(Player player, double count) {
        doSellTransaction(player, player.getInventory(), count);
    }

    public void doSellTransaction(Player player, Inventory inventory, double count) {
        if (count == 0) {
            player.sendMessage(ActionShop.formatNothingMessage());
            return;
        }
        ActionShop shop = buildSellShop(player);
        ShopWantsItem multipliedCost = (ShopWantsItem) shop.cost.multiply(count);
        if (multipliedCost.canAfford(inventory)) {
            multipliedCost.deduct(inventory);
            double refund = shop.reward.multiply(count).grantOrRefund(player);
            if (refund > 0) {
                // refunds DO NOT allow refunding
                shop.cost.multiply(refund).grantOrRefund(player);
                player.sendMessage(shop.formatRefundMessage(player, refund));
            }
            player.sendMessage(shop.formatPurchaseMessage(player, count - refund));
        } else {
            player.sendMessage(ActionShop.formatNothingMessage());
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        ActionShop buy = buildBuyShop(player), sell = buildSellShop(player);
        switch (e.getClick()) {
            case LEFT:
            case SHIFT_LEFT:
                if (buy != null)
                    buy.onClick(e);
                break;
            case RIGHT:
            case SHIFT_RIGHT:
                if (sell != null)
                    sell.onClick(e);
                break;
            case MIDDLE:
                if (sellAll && sell != null)
                    sell.sellAll(player);
                break;
        }
    }

    @Override
    public void influenceItem(Player player, ItemStack readonlyStack, ItemStack stack) {
        super.influenceItem(player, readonlyStack, stack);
        ItemBuilder modifier = ItemBuilder.from(stack);
        if (buyPrice > 0) {
            modifier.addLores(
                    I18n.translate("worstshop.messages.shops.buy-for", formatPrice(buyPrice))
            );
        }
        if (sellPrice > 0) {
            modifier.addLores(
                    I18n.translate("worstshop.messages.shops.sell-for", formatPrice(sellPrice))
            );
        }
        if (sellAll && sellPrice > 0) {
            modifier.addLores(
                    I18n.translate("worstshop.messages.shops.sell-all", formatPrice(sellPrice))
            );
        }
    }

    public static String formatPrice(double money) {
        return WorstShop.get().economy.getProvider().format(money);
    }

    @Override
    public void readElement(ShopElement element) {
        parentElement = element.clone();
        if (element instanceof StaticShopElement) {
            // add to ItemShop
            StaticShopElement elem = (StaticShopElement) element;
            getItemShops(elem.stack.getType()).add(new ItemShop(this, elem.viewPerm));
        } else {
            // unsupported
        }
    }

    private static List<ItemShop> getItemShops(Material key) {
        return ShopManager.ITEM_SHOPS.computeIfAbsent(key, k->Lists.newArrayList());
    }
}
