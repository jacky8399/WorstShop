package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.i18n.ComponentTranslatable;
import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.shops.*;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import com.jacky8399.worstshop.shops.commodity.CommodityItem;
import com.jacky8399.worstshop.shops.commodity.CommodityMoney;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import com.jacky8399.worstshop.shops.elements.ConditionalShopElement;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Shop but much more concise and only supports items
 */
public class ActionItemShop extends Action {
    ShopElement parentElement;
    @Nullable
    ItemStack overrideStack;
    ShopReference parentShop;
    boolean canSellAll;
    public double buyPrice, sellPrice;
    @Nullable
    public PlayerPurchases.RecordTemplate buyLimitTemplate, sellLimitTemplate;
    public int buyLimit, sellLimit;
    public HashSet<CommodityItem.ItemMatcher> itemMatchers = Sets.newHashSet(CommodityItem.ItemMatcher.SIMILAR);
    @NotNull
    ImmutableList<NamespacedKey> accepted = ImmutableList.of();

    // for serialization purposes
    public transient boolean usedStringShorthand = false, usedPriceShortcut = false, usedLimitShortcut = false;

    public ActionItemShop(@NotNull ShopReference parentShop, ShopElement parentElement, double buyPrice, double sellPrice,
                          @NotNull ImmutableList<NamespacedKey> accepted) {
        super(null);
        this.parentShop = parentShop;
        this.parentElement = parentElement;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        checkPrices();
        canSellAll = sellPrice != 0;
        this.accepted = accepted;
    }

    // shortcut
    public ActionItemShop(String input) {
        super(null);
        input = input.trim();
        String[] prices = input.split(" ");
        if (prices.length != 2)
            throw new IllegalArgumentException(input + " is not in the correct format!");

        readParent();
        String key = null;
        String[] format;
        format = prices[0].split("/", 3);
        buyPrice = Double.parseDouble(format[0]);
        if (format.length == 3) {
            buyLimit = Integer.parseInt(format[1]);
            buyLimitTemplate = PlayerPurchases.RecordTemplate.fromShorthand(key = guessKeyFromParent(), format[2]);
        }
        format = prices[1].split("/", 3);
        sellPrice = Double.parseDouble(format[0]);
        if (format.length == 3) {
            sellLimit = Integer.parseInt(format[1]);
            sellLimitTemplate = PlayerPurchases.RecordTemplate.fromShorthand(key == null ? guessKeyFromParent() : key, format[2]);
        }
        checkPrices();
        canSellAll = sellPrice != 0;
        usedStringShorthand = true;

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

        // accepts
        Optional<NamespacedKey> acceptedString = yaml.tryFind("accepts", String.class).map(CommodityItem.STRING_TO_KEY);
        if (acceptedString.isPresent()) {
            accepted = ImmutableList.of(acceptedString.get());
        } else {
            Optional<List<String>> acceptedList = yaml.findList("accepts", String.class);
            acceptedList.ifPresent(strings -> accepted = strings.stream().map(CommodityItem.STRING_TO_KEY)
                    .filter(Objects::nonNull)
                    .collect(ImmutableList.toImmutableList()));
        }

        // allow changing the item bought/sold
        Optional<Config> itemOverride = yaml.find("item", Config.class);
        itemOverride.ifPresent(config -> overrideStack = StaticShopElement.parseItemStack(config));
        if (overrideStack != null) {
            parentShop = ShopReference.of(ParseContext.findLatest(Shop.class));
            // add to item shop
            ItemShop shop = new ItemShop(this, ConditionConstant.TRUE);
            getItemShops(overrideStack.getType()).add(shop);
        } else {
            if (!readParent()) {
                WorstShop.get().logger.warning(parentElement.getClass().getSimpleName() + " is not supported! This item shop may not work!");
                WorstShop.get().logger.warning("Offending action: " + yaml.getPath());
            }
        }
    }

    private String guessKeyFromParent() {
        if (parentElement instanceof StaticShopElement element) {
            return element.rawStack.getType().getKey().toString();
        }
        WorstShop.get().logger.warning("Not sure about parent element, using \"item_shop\" as key");
        WorstShop.get().logger.warning("Offending action: " + ParseContext.getHierarchy());
        return "item_shop";
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

        if (accepted.size() != 0) {
            map.put("accepts", accepted.stream().map(CommodityItem.KEY_TO_STRING).collect(Collectors.toList()));
        }

        if (overrideStack != null) {
            map.put("item", StaticShopElement.serializeItemStack(overrideStack, new LinkedHashMap<>()));
        }
        return map;
    }

    private boolean readParent() {
        parentShop = ShopReference.of(ParseContext.findLatest(Shop.class));
        ShopElement element = ParseContext.findLatest(ShopElement.class);
        if (parentShop == ShopReference.empty() || element == null)
            throw new IllegalStateException("Couldn't find parent shop / element! Not in parse context?");
        parentElement = element.clone();

        return addToItemShop(parentElement).size() != 0;
    }

    private List<ItemShop> addToItemShop(ShopElement element) {
        if (element instanceof StaticShopElement staticElem) {
            ItemShop shop = new ItemShop(this, element.condition);
            getItemShops(staticElem.rawStack.getType()).add(shop);
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
    public double getDiscount(Player player) {
        ItemStack stack = getTargetItemStack(player);
        return ShopDiscount.calcFinalPrice(ShopDiscount.findApplicableEntries(parentShop.get(), stack.getType(), player));
    }

    public ItemStack getTargetItemStack(Player player) {
        ItemStack stack;
        if (overrideStack != null) {
            PlaceholderContext context = PlaceholderContext.guessContext(player);
            stack = Placeholders.setPlaceholders(overrideStack, context);
        } else {
            stack = parentElement instanceof StaticShopElement sse ?
                    sse.createPlaceholderStack(player) :
                    parentElement.createStack(player);
        }
        return ItemUtils.removeSafetyKey(stack);
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
        ItemStack target = getTargetItemStack(player);
        return sellPrice > 0 ?
                new ActionShop(
                        new CommodityItem(target, accepted, target.getAmount(), 1).setItemMatchers(itemMatchers),
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

    ComponentTranslatable SELL_ALL_MESSAGE = I18n.createComponentTranslatable("worstshop.messages.shops.sell-all");
    @Override
    public void influenceItem(Player player, ItemStack readonlyStack, ItemBuilder builder) {
        double discount = getDiscount(player);
        var lines = new ArrayList<Component>();
        if (buyPrice > 0) {
            lines.addAll(ItemShopFormatter.formatServerBuy(this, player));
        }
        if (sellPrice > 0) {
            lines.addAll(ItemShopFormatter.formatServerSell(this, player));
        }
        if (canSellAll && sellPrice > 0) {
            lines.add(SELL_ALL_MESSAGE.apply(formatPriceDiscountComponent(sellPrice, discount)));
        }
        builder.addLore(lines);
    }

    public Component formatPriceDiscountComponent(double price, double discount) {
        Component priceComponent = CommodityMoney.formatMoneyComponent(price);
        if (discount == 1) {
            return priceComponent;
        }
        return Component.textOfChildren(
                priceComponent.decorate(TextDecoration.STRIKETHROUGH),
                Component.space(),
                CommodityMoney.formatMoneyComponent(price * discount)
        );
    }

    public static String formatPrice(double money) {
        return WorstShop.get().economy.getProvider().format(money);
    }

    private static List<ItemShop> getItemShops(Material key) {
        return ShopManager.ITEM_SHOPS.computeIfAbsent(key, k->new ArrayList<>());
    }
}
