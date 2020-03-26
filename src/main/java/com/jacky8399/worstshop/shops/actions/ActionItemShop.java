package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.ItemShop;
import com.jacky8399.worstshop.shops.ShopDiscount;
import com.jacky8399.worstshop.shops.ShopManager;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import com.jacky8399.worstshop.shops.wants.ShopWantsItem;
import com.jacky8399.worstshop.shops.wants.ShopWantsMoney;
import org.bukkit.ChatColor;
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
    public ActionItemShop(String input) {
        super(null);
        String[] prices = input.split("\\s|,");
        buyPrice = Double.parseDouble(prices[0].trim());
        sellPrice = Double.parseDouble(prices[1].trim());

        sellAll = sellPrice != 0;
    }

    public ActionItemShop(Map<String, Object> yaml) {
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

    private double getDiscount(Player player) {
        ItemStack stack = getTargetItemStack(player);
        return ShopDiscount.calcFinalPrice(ShopDiscount.findApplicableEntries(parentElement.owner, stack.getType(), player));
    }

    public ItemStack getTargetItemStack(Player player) {
        return isStackDynamic ? parentElement.createStack(player) : ((StaticShopElement) parentElement).createPlaceholderStack(player);
    }

    public ShopWantsItem buildWantsItem(Player player) {
        // try to get static stack
        return new ShopWantsItem(getTargetItemStack(player));
    }

    public ActionShop buildBuyShop(Player player) {
        double discount = getDiscount(player);
        return buyPrice > 0 ? new ActionShop(new ShopWantsMoney(buyPrice * discount), buildWantsItem(player)) : null;
    }

    public ActionShop buildSellShop(Player player) {
        double discount = getDiscount(player);
        return sellPrice > 0 ? new ActionShop(buildWantsItem(player), new ShopWantsMoney(sellPrice * discount)) : null;
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
        double discount = getDiscount(player);
        ItemBuilder modifier = ItemBuilder.from(stack);
        if (buyPrice > 0) {
            modifier.addLores(
                    I18n.translate("worstshop.messages.shops.buy-for", formatPriceDiscount(buyPrice, discount))
            );
        }
        if (sellPrice > 0) {
            modifier.addLores(
                    I18n.translate("worstshop.messages.shops.sell-for", formatPriceDiscount(sellPrice, discount))
            );
        }
        if (sellAll && sellPrice > 0) {
            modifier.addLores(
                    I18n.translate("worstshop.messages.shops.sell-all", formatPriceDiscount(sellPrice, discount))
            );
        }
    }

    public String formatPriceDiscount(double price, double discount) {
        if (discount == 1) {
            return formatPrice(price);
        }
        return ChatColor.STRIKETHROUGH + formatPrice(price) + ChatColor.RESET + " " + formatPrice(price * discount);
    }

    public static String formatPrice(double money) {
        return WorstShop.get().economy.getProvider().format(money);
    }

    @Override
    public void readElement(ShopElement element) {
        parentElement = element.clone();
        parentElement.owner = element.owner;
        if (element instanceof StaticShopElement) {
            // add to ItemShop
            StaticShopElement elem = (StaticShopElement) element;
            getItemShops(elem.rawStack.getType()).add(new ItemShop(this, elem.condition));
        } else {
            // unsupported
        }
    }

    private static List<ItemShop> getItemShops(Material key) {
        return ShopManager.ITEM_SHOPS.computeIfAbsent(key, k->Lists.newArrayList());
    }
}
