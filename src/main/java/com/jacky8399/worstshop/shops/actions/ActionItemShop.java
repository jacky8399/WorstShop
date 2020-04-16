package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.PurchaseRecords;
import com.jacky8399.worstshop.shops.*;
import com.jacky8399.worstshop.shops.elements.DynamicShopElement;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ActionItemShop extends Action {

    boolean isStackDynamic = false;
    ShopElement parentElement;
    Shop parentShop;
    boolean sellAll;
    public double buyPrice = 0, sellPrice = 0;
    public PurchaseRecords.RecordTemplate buyLimitTemplate, sellLimitTemplate;
    public int buyLimit, sellLimit;
    public HashSet<ShopWantsItem.ItemMatcher> itemMatchers = Sets.newHashSet(ShopWantsItem.SIMILAR);

    // shortcut
    public ActionItemShop(String input) {
        super(null);
        String[] prices = input.split("\\s|,");
        buyPrice = Double.parseDouble(prices[0].trim());
        sellPrice = Double.parseDouble(prices[1].trim());

        sellAll = sellPrice != 0;

        getParent();
    }

    public ActionItemShop(Map<String, Object> yaml) {
        super(yaml);

        if (yaml.containsKey("buy-price")) {
            buyPrice = ((Number) yaml.get("buy-price")).doubleValue();
        }
        if (yaml.containsKey("sell-price")) {
            sellPrice = ((Number) yaml.get("sell-price")).doubleValue();
        }

        // shortcut
        if (yaml.containsKey("prices")) {
            String[] prices = ((String) yaml.get("prices")).split("\\s|,");
            buyPrice = Double.parseDouble(prices[0].trim());
            sellPrice = Double.parseDouble(prices[1].trim());
        }

        // item matchers
        if (yaml.containsKey("matches") /* not a typo */) {
            itemMatchers.clear();
            ((List<String>) yaml.get("matches")).stream().map(s -> s.toLowerCase().replace(' ', '_'))
                    .map(ShopWantsItem.ITEM_MATCHERS::get).forEach(itemMatchers::add);
        }

        // purchase limits
        if (yaml.containsKey("purchase-limits")) {
            Map<String, Object> purchaseLimitsYaml = (Map<String, Object>) yaml.get("purchase-limits");
            if (purchaseLimitsYaml.containsKey("both")) {
                Map<String, Object> purchaseLimitYaml = (Map<String, Object>) purchaseLimitsYaml.get("both");
                buyLimitTemplate = sellLimitTemplate = PurchaseRecords.RecordTemplate.fromMap(purchaseLimitYaml);
                buyLimit = sellLimit = ((Number) purchaseLimitYaml.get("limit")).intValue();
            } else {
                if (purchaseLimitsYaml.containsKey("buy")) {
                    Map<String, Object> purchaseLimitYaml = (Map<String, Object>) purchaseLimitsYaml.get("buy");
                    buyLimitTemplate = PurchaseRecords.RecordTemplate.fromMap(purchaseLimitYaml);
                    buyLimit = ((Number) purchaseLimitYaml.get("limit")).intValue();
                }
                if (purchaseLimitsYaml.containsKey("sell")) {
                    Map<String, Object> purchaseLimitYaml = (Map<String, Object>) purchaseLimitsYaml.get("sell");
                    sellLimitTemplate = PurchaseRecords.RecordTemplate.fromMap(purchaseLimitYaml);
                    sellLimit = ((Number) purchaseLimitYaml.get("limit")).intValue();
                }
            }
        }

        sellAll = (boolean) yaml.getOrDefault("allow-sell-all", true);

        getParent();
    }

    private void getParent() {
        parentShop = ParseContext.findLatest(Shop.class);
        parentElement = ParseContext.findLatest(ShopElement.class).clone();
        isStackDynamic = parentElement instanceof DynamicShopElement;

        if (parentElement instanceof StaticShopElement) {
            getItemShops(((StaticShopElement) parentElement).rawStack.getType()).add(new ItemShop(this, ((StaticShopElement) parentElement).condition));
        }
    }

    private double getDiscount(Player player) {
        ItemStack stack = getTargetItemStack(player);
        return ShopDiscount.calcFinalPrice(ShopDiscount.findApplicableEntries(parentShop, stack.getType(), player));
    }

    public ItemStack getTargetItemStack(Player player) {
        return isStackDynamic ? parentElement.createStack(player) : ((StaticShopElement) parentElement).createPlaceholderStack(player);
    }

    public ActionShop buildBuyShop(Player player) {
        double discount = getDiscount(player);
        return buyPrice > 0 ?
                new ActionShop(
                        new ShopWantsMoney(buyPrice * discount),
                        new ShopWantsItem(getTargetItemStack(player)).setItemMatchers(itemMatchers),
                        buyLimitTemplate, buyLimit
                ) : null;
    }

    public ActionShop buildSellShop(Player player) {
        double discount = getDiscount(player);
        return sellPrice > 0 ?
                new ActionShop(
                        new ShopWantsItem(getTargetItemStack(player)).setItemMatchers(itemMatchers),
                        new ShopWantsMoney(sellPrice * discount),
                        sellLimitTemplate, sellLimit
                ) : null;
    }

    public static ShopWantsItem rerouteWantToInventory(ShopWantsItem item, Inventory inventory) {
        return new ShopWantsItem(item) {
            @Override
            public ShopWants multiply(double multiplier) {
                return rerouteWantToInventory((ShopWantsItem) super.multiply(multiplier), inventory);
            }

            @Override
            public boolean canAfford(Player player) {
                return canAfford(inventory);
            }

            @Override
            public int getMaximumMultiplier(Player player) {
                return getMaximumMultiplier(inventory);
            }

            @Override
            public void deduct(Player player) {
                deduct(inventory);
            }

            @Override
            public double grantOrRefund(Player player) {
                grant(inventory);
                return 0;
            }

            @Override
            public ShopWants adjustForPlayer(Player player) {
                return rerouteWantToInventory((ShopWantsItem) super.adjustForPlayer(player), inventory);
            }
        };
    }

    public static ShopWantsItem rerouteWantToStack(ShopWantsItem item, ItemStack stack) {
        return new ShopWantsItem(item) {
            @Override
            public ShopWants multiply(double multiplier) {
                return rerouteWantToStack((ShopWantsItem) super.multiply(multiplier), stack);
            }

            @Override
            public boolean canAfford(Player player) {
                return canAfford(stack);
            }

            @Override
            public int getMaximumMultiplier(Player player) {
                return getMaximumMultiplier(stack);
            }

            @Override
            public void deduct(Player player) {
                deduct(stack);
            }

            @Override
            public ShopWants adjustForPlayer(Player player) {
                return rerouteWantToStack((ShopWantsItem) super.adjustForPlayer(player), stack);
            }
        };
    }

    public void doBuyTransaction(Player player, double count) {
        if (count == 0) {
            player.sendMessage(ActionShop.formatNothingMessage());
            return;
        }
        ActionShop shop = buildBuyShop(player).adjustForPlayer(player);
        shop.doTransaction(player, count);
    }

    public void doSellTransaction(Player player, Inventory inventory, double count) {
        if (count == 0) {
            player.sendMessage(ActionShop.formatNothingMessage());
            return;
        }
        ActionShop shop = buildSellShop(player);
        // HACK: reroute shop cost
        shop.cost = rerouteWantToInventory((ShopWantsItem) shop.cost, inventory);
        shop.doTransaction(player, count);
    }
    public void doSellTransaction(Player player, ItemStack stack, double count) {
        if (count == 0) {
            player.sendMessage(ActionShop.formatNothingMessage());
            return;
        }
        ActionShop shop = buildSellShop(player);
        // HACK: reroute shop cost
        shop.cost = rerouteWantToStack((ShopWantsItem) shop.cost, stack);
        shop.doTransaction(player, count);
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

    private static List<ItemShop> getItemShops(Material key) {
        return ShopManager.ITEM_SHOPS.computeIfAbsent(key, k->Lists.newArrayList());
    }
}
