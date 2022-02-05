package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Sets;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.PlayerPurchases;
import com.jacky8399.worstshop.shops.*;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import com.jacky8399.worstshop.shops.commodity.CommodityItem;
import com.jacky8399.worstshop.shops.commodity.CommodityMoney;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.elements.ConditionalShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shop but much more concise and only supports items
 */
public class ActionItemShop extends Action {
    ShopElement parentElement;
    ShopReference parentShop;
    boolean canSellAll;
    public double buyPrice, sellPrice;
    @Nullable
    public PlayerPurchases.RecordTemplate buyLimitTemplate, sellLimitTemplate;
    public int buyLimit, sellLimit;
    public HashSet<CommodityItem.ItemMatcher> itemMatchers = Sets.newHashSet(CommodityItem.ItemMatcher.SIMILAR);

    // for serialization purposes
    public transient boolean usedStringShorthand = false, usedPriceShortcut = false, usedLimitShortcut = false;

    public ActionItemShop(@NotNull ShopReference parentShop, ShopElement parentElement, double buyPrice, double sellPrice) {
        super(null);
        this.parentShop = parentShop;
        this.parentElement = parentElement;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        checkPrices();
        canSellAll = sellPrice != 0;
    }

    // shortcut
    public ActionItemShop(String input) {
        super(null);
        String[] prices = input.split("\\s|,");
        if (prices.length != 2)
            throw new IllegalArgumentException(input + " is not in the correct format!");
        buyPrice = Double.parseDouble(prices[0].trim());
        sellPrice = Double.parseDouble(prices[1].trim());
        checkPrices();
        canSellAll = sellPrice != 0;
        usedStringShorthand = true;

        readParent();
    }

    public ActionItemShop(Config yaml) {
        super(yaml);

        buyPrice = yaml.find("buy-price", Double.class).orElse(0D);
        sellPrice = yaml.find("sell-price", Double.class).orElse(0D);

        // shortcut
        yaml.find("prices", String.class).ifPresent(price -> {
            String[] prices = price.split("\\s|,");
            buyPrice = Double.parseDouble(prices[0].trim());
            sellPrice = Double.parseDouble(prices[1].trim());
            usedPriceShortcut = true;
        });
        checkPrices();
        canSellAll = sellPrice != 0 && yaml.find("allow-sell-all", Boolean.class).orElse(true);

        // item matchers
        yaml.findList("matches", String.class).ifPresent(list -> {
            itemMatchers.clear();
            list.stream().map(s -> s.toLowerCase(Locale.ROOT).replace(' ', '_'))
                    .map(CommodityItem.ItemMatcher.ITEM_MATCHERS::get)
                    .filter(Objects::nonNull)
                    .forEach(itemMatchers::add);
        });

        // purchase limits
        yaml.find("purchase-limits", Config.class).ifPresent(purchaseLimitsYaml -> {
            Optional<Config> both = purchaseLimitsYaml.find("both", Config.class);
            if (both.isPresent()) {
                buyLimitTemplate = sellLimitTemplate = PlayerPurchases.RecordTemplate.fromConfig(both.get());
                buyLimit = sellLimit = both.get().get("limit", Integer.class);
                usedLimitShortcut = true;
            } else {
                purchaseLimitsYaml.find("buy", Config.class).ifPresent(purchaseLimitYaml -> {
                    buyLimitTemplate = PlayerPurchases.RecordTemplate.fromConfig(purchaseLimitYaml);
                    buyLimit = purchaseLimitYaml.get("limit", Integer.class);
                });
                purchaseLimitsYaml.find("sell", Config.class).ifPresent(purchaseLimitYaml -> {
                    sellLimitTemplate = PlayerPurchases.RecordTemplate.fromConfig(purchaseLimitYaml);
                    sellLimit = purchaseLimitYaml.get("limit", Integer.class);
                });
            }
        });

        readParent();
    }

    public void checkPrices() throws IllegalArgumentException {
        if (!Double.isFinite(buyPrice) || buyPrice < 0)
            throw new IllegalArgumentException(buyPrice + " is not a valid buy price!");
        if (!Double.isFinite(sellPrice) || sellPrice < 0)
            throw new IllegalArgumentException(sellPrice + " is not a valid sell price!");
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "item shop");
        if (usedPriceShortcut) {
            map.put("prices", buyPrice + " " + sellPrice);
        } else {
            if (buyPrice > 0)
                map.put("buy-price", buyPrice);
            if (sellPrice > 0)
                map.put("sell-price", sellPrice);
        }
        // item matcher isn't default
        if (!(itemMatchers.size() == 1 && itemMatchers.contains(CommodityItem.ItemMatcher.SIMILAR)))
            map.put("matches", itemMatchers.stream()
                    .map(matcher -> matcher.name.toLowerCase(Locale.ROOT).replace('_', ' '))
                    .collect(Collectors.toList()));
        HashMap<String, Object> limitMap = new HashMap<>();
        if (usedLimitShortcut) {
            HashMap<String, Object> innerMap = new HashMap<>();
            innerMap.put("limit", buyLimit);
            buyLimitTemplate.toMap(innerMap);
            limitMap.put("both", innerMap);
        } else {
            if (buyLimitTemplate != null) {
                HashMap<String, Object> innerMap = new HashMap<>();
                innerMap.put("limit", buyLimit);
                buyLimitTemplate.toMap(innerMap);
                limitMap.put("buy", innerMap);
            }
            if (sellLimitTemplate != null) {
                HashMap<String, Object> innerMap = new HashMap<>();
                innerMap.put("limit", sellLimit);
                sellLimitTemplate.toMap(innerMap);
                limitMap.put("sell", innerMap);
            }
        }
        // write only if at least one is present
        if (buyLimitTemplate != null || sellLimitTemplate != null)
            map.put("purchase-limits", limitMap);

        if (!canSellAll && sellPrice != 0)
            map.put("allow-sell-all", false);
        return map;
    }

    private void readParent() {
        parentShop = ShopReference.of(ParseContext.findLatest(Shop.class));
        ShopElement element = ParseContext.findLatest(ShopElement.class);
        if (parentShop == ShopReference.empty() || element == null)
            throw new IllegalStateException("Couldn't find parent shop / element! Not in parse context?");
        parentElement = element.clone();

        addToItemShop(parentElement);
    }

    private List<ItemShop> addToItemShop(ShopElement element) {
        if (element instanceof StaticShopElement) {
            ItemShop shop = new ItemShop(this, element.condition);
            getItemShops(((StaticShopElement) element).rawStack.getType()).add(shop);
            return Collections.singletonList(shop);
        } else if (element instanceof ConditionalShopElement conditional) {
            List<ItemShop> shopsTrue = addToItemShop(conditional.elementTrue);
            shopsTrue.forEach(shop -> shop.condition = conditional.condition.and(shop.condition));
            if (conditional.elementFalse != null) {
                Condition negatedCondition = conditional.condition.negate();

                List<ItemShop> shopsFalse = addToItemShop(conditional.elementFalse);
                shopsFalse.forEach(shop -> shop.condition = negatedCondition.and(shop.condition));

                shopsTrue.addAll(shopsFalse);
            }
            return shopsTrue;
        }
        return Collections.emptyList();
    }

    /**
     * Gets all applicable discounts for the player
     * @param player Player
     * @return The cost multiplier
     */
    private double getDiscount(Player player) {
        ItemStack stack = getTargetItemStack(player);
        return ShopDiscount.calcFinalPrice(ShopDiscount.findApplicableEntries(parentShop.get(), stack.getType(), player));
    }

    public ItemStack getTargetItemStack(Player player) {
        return parentElement instanceof StaticShopElement sse ?
                sse.createPlaceholderStack(player) :
                parentElement.createStack(player);
    }

    // builds the actual shops used for displaying the GUI
    public ActionShop buildBuyShop(Player player) {
        double discount = getDiscount(player);
        return buyPrice > 0 ?
                new ActionShop(
                        new CommodityMoney(buyPrice * discount),
                        new CommodityItem(getTargetItemStack(player)).setItemMatchers(itemMatchers),
                        buyLimitTemplate, buyLimit, Integer.MAX_VALUE
                ) : null;
    }

    public ActionShop buildSellShop(Player player) {
        double discount = getDiscount(player);
        return sellPrice > 0 ?
                new ActionShop(
                        new CommodityItem(getTargetItemStack(player)).setItemMatchers(itemMatchers),
                        new CommodityMoney(sellPrice * discount),
                        sellLimitTemplate, sellLimit, Integer.MAX_VALUE
                ) : null;
    }

    public static CommodityItem rerouteToInventory(CommodityItem item, Inventory inventory) {
        return new CommodityItem(item) {
            @Override
            public Commodity multiply(double multiplier) {
                return rerouteToInventory((CommodityItem) super.multiply(multiplier), inventory);
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
            public Commodity adjustForPlayer(Player player) {
                return rerouteToInventory((CommodityItem) super.adjustForPlayer(player), inventory);
            }
        };
    }

    public static CommodityItem rerouteToStack(CommodityItem item, ItemStack stack) {
        return new CommodityItem(item) {
            @Override
            public Commodity multiply(double multiplier) {
                return rerouteToStack((CommodityItem) super.multiply(multiplier), stack);
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
            public Commodity adjustForPlayer(Player player) {
                return rerouteToStack((CommodityItem) super.adjustForPlayer(player), stack);
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
        shop.cost = rerouteToInventory((CommodityItem) shop.cost, inventory);
        shop.doTransaction(player, count);
    }

    public void doSellTransaction(Player player, ItemStack stack, double count) {
        if (count == 0) {
            player.sendMessage(ActionShop.formatNothingMessage());
            return;
        }
        ActionShop shop = buildSellShop(player);
        // HACK: reroute shop cost
        shop.cost = rerouteToStack((CommodityItem) shop.cost, stack);
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
                if (canSellAll && sell != null)
                    sell.sellAll(player);
                break;
        }
    }

    I18n.Translatable BUY_PRICE_MESSAGE = I18n.createTranslatable("worstshop.messages.shops.buy-for"),
            SELL_PRICE_MESSAGE = I18n.createTranslatable("worstshop.messages.shops.sell-for"),
            SELL_ALL_MESSAGE = I18n.createTranslatable("worstshop.messages.shops.sell-all");
    @Override
    public void influenceItem(Player player, ItemStack readonlyStack, ItemStack stack) {
        double discount = getDiscount(player);
        ItemBuilder modifier = ItemBuilder.from(stack);
        if (buyPrice > 0) {
            modifier.addLores(BUY_PRICE_MESSAGE.apply(formatPriceDiscount(buyPrice, discount)));
        }
        if (sellPrice > 0) {
            modifier.addLores(SELL_PRICE_MESSAGE.apply(formatPriceDiscount(sellPrice, discount)));
        }
        if (canSellAll && sellPrice > 0) {
            modifier.addLores(SELL_ALL_MESSAGE.apply(formatPriceDiscount(sellPrice, discount)));
        }
        modifier.build(); // future-proof
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
        return ShopManager.ITEM_SHOPS.computeIfAbsent(key, k->new ArrayList<>());
    }
}
