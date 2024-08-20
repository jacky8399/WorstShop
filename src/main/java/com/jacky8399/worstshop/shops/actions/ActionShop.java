package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.i18n.ComponentTranslatable;
import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import com.jacky8399.worstshop.shops.commodity.CommodityItem;
import com.jacky8399.worstshop.shops.commodity.CommodityMultiple;
import com.jacky8399.worstshop.shops.commodity.IFlexibleCommodity;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.BundleMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Opens a GUI where players can exchange for a reward with a predefined cost
 */
public class ActionShop extends Action {
    public Commodity cost, reward;
    public PlayerPurchases.RecordTemplate purchaseLimitTemplate;
    public int purchaseLimit;
    // Maximum purchase per UI interaction
    public int maxPurchase;

    public ActionShop(Config yaml) {
        super(yaml);
        this.cost = yaml.tryFind("cost", String.class, Config.class)
                .map(Commodity::fromObject)
                // check types even in lists
                // the previous implementation might have allowed lists within lists
                .orElseGet(() -> yaml.findList("cost", String.class, Config.class)
                        .map(list -> (Commodity) // cast to ensure return type is Commodity
                                list.stream()
                                        .map(Commodity::fromObject)
                                        .collect(Collectors.collectingAndThen(Collectors.toList(), CommodityMultiple::new))
                        ).orElseGet(ActionShop::findParent)
                );

        this.reward = yaml.tryFind("reward", String.class, Config.class)
                .map(Commodity::fromObject)
                // check types even in lists
                // the previous implementation might have allowed lists within lists
                .orElseGet(() -> yaml.findList("reward", String.class, Config.class)
                        .map(list -> (Commodity) // cast to ensure return type is Commodity
                                list.stream()
                                        .map(Commodity::fromObject)
                                        .collect(Collectors.collectingAndThen(Collectors.toList(), CommodityMultiple::new))
                        ).orElseGet(ActionShop::findParent)
                );

        yaml.find("purchase-limit", Config.class).ifPresent(purchaseLimitYaml -> {
            purchaseLimitTemplate = PlayerPurchases.RecordTemplate.fromConfig(purchaseLimitYaml);
            purchaseLimit = purchaseLimitYaml.get("limit", Integer.class);
        });

        this.maxPurchase = yaml.find("max-puchase", Integer.class).orElse(Integer.MAX_VALUE);
    }

    private static Commodity findParent() {
        StaticShopElement element = ParseContext.findLatest(StaticShopElement.class);
        return element != null ? new CommodityItem(element.rawStack.clone()) : null;
    }

    public ActionShop(Commodity cost, Commodity reward) {
        this(cost, reward, null, 0, Integer.MAX_VALUE);
    }

    public ActionShop(Commodity cost, Commodity reward, PlayerPurchases.RecordTemplate purchaseLimitTemplate, int purchaseLimit, int maxPurchase) {
        super(null);
        this.cost = cost;
        this.reward = reward;
        this.purchaseLimitTemplate = purchaseLimitTemplate;
        this.purchaseLimit = purchaseLimit;
        this.maxPurchase = maxPurchase;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "shop");
        // might be a string/list, so use special method
        map.put("cost", cost.toSerializable(new HashMap<>()));
        map.put("reward", reward.toSerializable(new HashMap<>()));
        if (purchaseLimitTemplate != null) {
            HashMap<String, Object> limitMap = new HashMap<>();
            limitMap.put("limit", purchaseLimit);
            purchaseLimitTemplate.toMap(limitMap);
            map.put("purchase-limit", limitMap);
        }
        if (maxPurchase != Integer.MAX_VALUE) {
            map.put("max-purchase", maxPurchase);
        }
        return map;
    }

    @Override
    public String toString() {
        return "ActionShop{cost=" + cost + ", reward=" + reward + "}";
    }

    private static final Set<UUID> noReimbursements = new HashSet<>();
    public void doTransaction(Player player, double count) {
        if (count == 0) {
            player.sendMessage(formatPurchaseMessage(player, 0));
            return;
        }
        String stage = "Adjusting shop";
        boolean shouldReimburse = false;
        try {
            ActionShop adjusted = adjustForPlayer(player);
            stage = "Calculating cost";
            Commodity multipliedCost = adjusted.cost.multiply(count);
            int playerMaxPurchases = getShopMaxPurchase(player);
            if (count > playerMaxPurchases) {
                player.sendMessage(formatPurchaseLimitMessage(player));
                return;
            }
            if (multipliedCost.canAfford(player)) {
                stage = "Deducting cost";
                shouldReimburse = true;
                multipliedCost.deduct(player);
                stage = "Granting reward";
                double refund = adjusted.reward.multiply(count).grantOrRefund(player);
                if (refund > 0) {
                    // refunds DO NOT allow refunding
                    stage = "Processing refunds";
                    adjusted.cost.multiply(refund).grantOrRefund(player);
                    player.sendMessage(formatRefundMessage(player, refund));
                }
                // record the sale
                if (purchaseLimitTemplate != null) {
                    PlayerPurchases records = PlayerPurchases.getCopy(player);
                    records.applyTemplate(purchaseLimitTemplate).addRecord(player, LocalDateTime.now(), (int) (count - refund));
                    records.updateFor(player);
                }
                player.sendMessage(formatPurchaseMessage(player, count - refund));
            } else {
                player.sendMessage(formatPurchaseMessage(player, 0));
            }
        } catch (Exception e) {
            RuntimeException wrapped = new RuntimeException(stage + " for player " + player.getName() + " in shop " + this, e);
            if (shouldReimburse && noReimbursements.add(player.getUniqueId())) {
                try {
                    adjustForPlayer(player).cost.multiply(count).grantOrRefund(player);
                    player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-reimbursement"));
                    player.sendMessage(formatRefundMessage(player, count));
                } catch (Exception notAgain) {
                    wrapped.addSuppressed(new RuntimeException("Reimbursing player", notAgain));
                }
            }
            String id = Exceptions.logException(wrapped);
            player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-error", id));
        }
    }

    public void sellAll(Player player) {
        ActionShop adjusted;
        int transactionCount;
        try {
            adjusted = adjustForPlayer(player);
            transactionCount = Math.min(adjusted.cost.getMaximumMultiplier(player), getShopMaxPurchase(player));
            adjusted.doTransaction(player, transactionCount);
        } catch (Exception e) {
            RuntimeException wrapped = new RuntimeException("Sell all operation for player " + player.getName() + " in shop " + this, e);
            String id = Exceptions.logException(wrapped);
            player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-error", id));
        }
    }

    public ActionShop adjustForPlayer(Player player) {
        Commodity newCost = cost, newReward = reward;
        if (cost instanceof IFlexibleCommodity flexible) {
            try {
                newCost = flexible.adjustForPlayer(player);
            } catch (Exception e) {
                throw new RuntimeException("Adjusting COST " + flexible + " for player " + player.getName() + " in shop " + this, e);
            }
        }
        if (reward instanceof IFlexibleCommodity flexible) {
            try {
                newReward = flexible.adjustForPlayer(player);
            } catch (Exception e) {
                throw new RuntimeException("Adjusting REWARD " + flexible + " for player " + player.getName() + " in shop " + this, e);
            }
        }
        return new ActionShop(newCost, newReward, purchaseLimitTemplate, purchaseLimit, maxPurchase);
    }

    public int getShopMaxPurchase(Player player) {
        if (purchaseLimitTemplate == null) {
            return maxPurchase;
        }
        PlayerPurchases records = PlayerPurchases.getCopy(player);
        PlayerPurchases.RecordStorage storage = records.applyTemplate(purchaseLimitTemplate);
        int totalPurchases = storage.getTotalPurchases();
        return Math.min(maxPurchase, purchaseLimit - totalPurchases);
    }

    // The overall
    public int getMaxPurchase(Player player) {
        return Math.max(1, Math.min(getShopMaxPurchase(player),
                Math.min(cost.getMaximumMultiplier(player), reward.getMaximumPurchase(player))));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Optional<InventoryContents> parentContentsOptional = WorstShop.get().inventories.getContents(player);
        if (parentContentsOptional.isPresent()) {
            InventoryContents parentContents = parentContentsOptional.get();
            SmartInventory inv = ShopGui.getInventory(this.adjustForPlayer(player), parentContents.inventory());
            InventoryUtils.openSafely(player, inv);
        }
    }

    public static String formatNothingMessage() {
        return I18n.translate("worstshop.messages.shops.transaction-nothing");
    }

    public String formatPurchaseMessage(Player player, double buyCount) {
        if (buyCount <= 0) {
            return formatNothingMessage();
        }
        ActionShop adjusted = adjustForPlayer(player);
        return I18n.translate("worstshop.messages.shops.transaction-message",
                buyCount,
                adjusted.cost.multiply(buyCount).getPlayerResult(player, Commodity.TransactionType.COST),
                adjusted.reward.multiply(buyCount).getPlayerResult(player, Commodity.TransactionType.REWARD)
        );
    }

    public String formatPurchaseLimitMessage(Player player) {
        PlayerPurchases records = PlayerPurchases.getCopy(player);
        PlayerPurchases.RecordStorage storage = records.applyTemplate(purchaseLimitTemplate);
        List<PlayerPurchases.Record> entries = storage.getEntries();
        Duration wait;
        if (entries.isEmpty()) {
            wait = Duration.ofSeconds(0);
        } else {
            LocalDateTime firstPurchase = entries.get(0).getKey();
            LocalDateTime nextPurchase = firstPurchase.plus(storage.retentionTime);
            wait = Duration.between(LocalDateTime.now(), nextPurchase);
        }
        return I18n.translate("worstshop.messages.shops.transaction-limit-reached",
                purchaseLimit,
                DateTimeUtils.formatTime(purchaseLimitTemplate.retentionTime()),
                DateTimeUtils.formatTime(wait)
        );
    }

    public String formatRefundMessage(Player player, double refundAmount) {
        if (refundAmount > 0) {
            ActionShop adjusted = adjustForPlayer(player);
            return I18n.translate("worstshop.messages.shops.transaction-refund",
                    refundAmount,
                    adjusted.cost.multiply(refundAmount).getPlayerResult(player, Commodity.TransactionType.REWARD)
            );
        }
        return "";
    }

    public static class ShopGui implements InventoryProvider {
        protected boolean firstClick = true;
        protected int buyCount = 1;
        protected int lastBuyCount = buyCount;
        protected final ActionShop shop;
        protected final Commodity cost;
        protected final Commodity reward;
        protected ShopElement costElem, rewardElem;

        protected ShopGui(ActionShop shop) {
            this.shop = shop;
            this.cost = shop.cost;
            this.reward = shop.reward;
        }

        public static SmartInventory getInventory(ActionShop shop, SmartInventory parent) {
            return WorstShop.buildGui("worstshop:shop_gui")
                    .title(I18n.translate("worstshop.messages.shops.shop"))
                    .type(InventoryType.CHEST).size(6, 9)
                    .provider(new ShopGui(shop)).parent(parent)
                    .build();
        }

        protected static final ClickableItem FILLER = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                .hideTooltip().toEmptyClickable();
        protected static final ClickableItem ARROW = ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                .hideTooltip().toEmptyClickable();
        protected static final ClickableItem GREEN = ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                .hideTooltip().toEmptyClickable();
        protected static final ClickableItem RED = ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                .hideTooltip().toEmptyClickable();

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(FILLER);

            updateCommodities(player, contents, false);

            updateItemCount(player, contents);
            if (cost.canMultiply() && reward.canMultiply() && shop.maxPurchase != 1)
                populateBuyCountChangeButtons(player, contents);
            updateAnimation(player, contents);

            // player balance etc
            contents.set(5, 4, ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(Component.textOfChildren(player.displayName()).color(NamedTextColor.WHITE))
                    .skullOwner(player)
                    .lore(cost.playerTrait(player))
                    .emptyClickable()
            );

            // ok button
            contents.set(5, 3, ClickableItem.of(
                    ItemBuilder.of(Material.GREEN_TERRACOTTA)
                            .name(I18n.translate("worstshop.messages.shops.buttons.confirm"))
                            .build(), this::doTransaction
            ));
            // cancel button
            contents.set(5, 5, ClickableItem.of(
                    ItemBuilder.of(Material.RED_TERRACOTTA)
                            .name(I18n.translate("worstshop.messages.shops.buttons.cancel"))
                            .build(), e -> contents.inventory().close(player)
            ));
            updatePurchaseLimit(player, contents);
            // maximize purchase button
            if (cost.canMultiply() && reward.canMultiply() && shop.maxPurchase != 1)
                contents.set(5, 8, ItemBuilder.of(Material.HOPPER)
                        .name(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.maximize-purchase"))
                        .toClickable(e -> {
                            buyCount = getMaxPurchase(player);
                            firstClick = false;
                        }));
        }

        @Override
        public void update(Player player, InventoryContents contents) {
            if (lastBuyCount != buyCount) {
                updateItemCount(player, contents);
                populateBuyCountChangeButtons(player, contents);
                lastBuyCount = buyCount;
            }
            updateAnimation(player, contents);
        }

        // animation
        // 0 to 4
        protected int animationSequence = 0;
        // 0 to 3
        protected int animationCooldown = 3;

        protected void doTransaction(InventoryClickEvent e) {
            Player player = (Player) e.getWhoClicked();
            Bukkit.getScheduler().runTask(WorstShop.get(), () -> {
                player.closeInventory();
                shop.doTransaction(player, buyCount);
            });
        }

        private static final int[] BUTTON_DELTA = new int[] {1, 4, 16, 64};

        private Consumer<InventoryClickEvent> createBuyCountChanger(int newValue) {
            return e -> {
                if (firstClick) { // update the +1 button
                    lastBuyCount = 0;
                }
                buyCount = newValue;
                firstClick = false;
            };
        }

        protected int getMaxPurchase(Player player) {
            // ensure that buyCount doesn't go below 1
            return shop.getMaxPurchase(player);
        }

        private static final String BUY_COUNTS_KEY = "worstshop.messages.shops.buy-counts.";
        private static final ComponentTranslatable
                INCREASE_BUY_COUNT_BY = I18n.createComponentTranslatable(BUY_COUNTS_KEY + "increase-by"),
                DECREASE_BUY_COUNT_BY = I18n.createComponentTranslatable(BUY_COUNTS_KEY + "decrease-by"),
                BUY_COUNT_RESULT = I18n.createComponentTranslatable(BUY_COUNTS_KEY + "change-result");
        // create button pairs
        protected void populateBuyCountChangeButtons(Player player, InventoryContents contents) {
            int oldCount = firstClick ? 0 : buyCount;
            int maxPurchase = getMaxPurchase(player);
            for (int i = 0; i < BUTTON_DELTA.length; i++) {
                int index = i + 1;
                int number = BUTTON_DELTA[i];
                var numberComponent = Component.text(number);

                // increase by x
                int increased = Math.min(oldCount + number, maxPurchase);
                // gray out if increment is not allowed
                Material incrementMaterial = increased == oldCount + number ? Material.LIME_STAINED_GLASS : Material.GRAY_STAINED_GLASS;
                contents.set(4, 4 + index, ItemBuilder.of(incrementMaterial)
                        .name(INCREASE_BUY_COUNT_BY.apply(numberComponent))
                        .lores(BUY_COUNT_RESULT.apply(Component.text(increased)))
                        .amount(number)
                        .toClickable(createBuyCountChanger(increased))
                );
                // decrease by x
                int decreased = Math.max(oldCount - number, 1);
                // gray out if decrement is not allowed
                Material decrementMaterial = decreased == oldCount - number ? Material.RED_STAINED_GLASS : Material.GRAY_STAINED_GLASS;
                contents.set(4, 4 - index, ItemBuilder.of(decrementMaterial)
                        .name(DECREASE_BUY_COUNT_BY.apply(numberComponent))
                        .lores(BUY_COUNT_RESULT.apply(Component.text(decreased)))
                        .amount(number)
                        .toClickable(createBuyCountChanger(decreased))
                );
            }
        }

        private static final Shop FAKE_SHOP = new Shop();
        static {
            FAKE_SHOP.id = "worstshop_internal:shop_gui";
        }
        private ShopRenderer renderer;
        protected void populateItems(Player player, ShopElement element, InventoryContents contents) {
            if (renderer == null) {
                renderer = new ShopRenderer(FAKE_SHOP, player);
            }
            PlaceholderContext context = new PlaceholderContext(renderer, element);
            List<RenderElement> items = element.getRenderElement(renderer, context);
            for (RenderElement item : items) {
                Collection<SlotPos> posList = item.positions();
                if (posList != null) {
                    for (SlotPos pos : posList) {
                        contents.set(pos, ClickableItem.empty(item.actualStack(renderer)));
                    }
                }
            }
        }

        protected void updateCommodities(Player player, InventoryContents contents, boolean dynamicOnly) {
            String stage = "cost";
            try {
                if (!dynamicOnly) {
                    costElem = cost.createElement(Commodity.TransactionType.COST).clone();
                    populateItems(player, costElem, contents);
                    stage = "reward";
                    rewardElem = reward.createElement(Commodity.TransactionType.REWARD).clone();
                    populateItems(player, rewardElem, contents);
                } else {
                    if (cost.isElementDynamic()) {
                        populateItems(player, costElem, contents);
                    }
                    stage = "reward";
                    if (reward.isElementDynamic()) {
                        populateItems(player, rewardElem, contents);
                    }
                }
            } catch (Exception ex) {
                // guess parent
                Optional<SmartInventory> parent = contents.inventory().getParent();
                String parentShop = "???";
                if (parent.isPresent() && parent.get().getProvider() instanceof ShopRenderer parentRenderer) {
                    parentShop = parentRenderer.toString();
                }
                RuntimeException wrapped = new RuntimeException("Rendering " + stage + " element for " + player.getName() + " (@" + parentShop + ")", ex);
                // spam the player
                contents.fill(ItemUtils.getClickableErrorItem(wrapped));
            }
        }

        protected void updateItemCount(Player player, InventoryContents contents) {
            Commodity realReward = reward.multiply(buyCount);
            var results = realReward.playerResult(player, Commodity.TransactionType.REWARD);
            List<Component> lore = new ArrayList<>(results.size() + 1);
            lore.add(I18n.translateAsComponent(BUY_COUNTS_KEY + "total-result", ""));
            lore.addAll(results);
            boolean useChest = buyCount > 99 && (cost instanceof CommodityItem || reward instanceof CommodityItem);
            contents.set(4, 4,
                    ItemBuilder.of(useChest ? Material.BUNDLE :
                                    buyCount != 0 ? Material.END_CRYSTAL : Material.BARRIER)
                            .name(I18n.translate(BUY_COUNTS_KEY + "total", buyCount))
                            .maxAmount(99)
                            .amount(Math.max(Math.min(useChest ? (int) Math.ceil(buyCount / 64f) : buyCount, 99), 1))
                            .lore(lore)
                            .meta(meta -> {
                                if (meta instanceof BundleMeta bundleMeta && realReward instanceof CommodityItem commodityItem) {
                                    // this will be so funny
                                    bundleMeta.setItems(commodityItem.getGrantedItems());
                                }
                            })
                            .toEmptyClickable()
            );
        }

        protected boolean canAfford(Player player) {
            return cost.multiply(buyCount).canAfford(player) && buyCount <= shop.getShopMaxPurchase(player);
        }

        private static final SlotPos[] CANT_AFFORD_CROSS = {SlotPos.of(0, 3), SlotPos.of(0, 5),
                SlotPos.of(1, 4), SlotPos.of(2, 3), SlotPos.of(2, 3), SlotPos.of(2, 5)};
        private boolean couldAfford = false;
        protected void updateAnimation(Player player, InventoryContents contents) {
            if (animationCooldown++ != 3) {
                return;
            }
            animationCooldown = 0;
            boolean canAfford = canAfford(player);
            if (canAfford != couldAfford) {
                // clear area
                contents.fillRect(0, 2, 2, 6, FILLER);
                couldAfford = canAfford;
            }
            int lastAnimationSequence = animationSequence;
            animationSequence = animationSequence == 4 ? 0 : animationSequence + 1;
            if (canAfford) {
                // clear last arrow
                int lastCol = lastAnimationSequence + 2;
                contents.set(0, lastCol, FILLER);
                contents.set(1, lastCol == 6 ? 2 : lastCol + 1, FILLER);
                contents.set(2, lastCol, FILLER);
                int col = animationSequence + 2;
                contents.set(0, col, ARROW);
                contents.set(1, col == 6 ? 2 : col + 1, ARROW);
                contents.set(2, col, ARROW);
            } else {
                ClickableItem clickableItem = animationSequence != 0 ? RED : FILLER;
                for (SlotPos pos : CANT_AFFORD_CROSS) {
                    contents.set(pos, clickableItem);
                }
            }
            // also update item lol
            if (animationSequence == 3) {
                // regularly refresh these too
                updatePurchaseLimit(player, contents);
                updateCommodities(player, contents, true);
            }
        }

        private static final String PURCHASE_LIMIT_KEY = I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.";
        private static final ComponentTranslatable
                PURCHASE_LIMIT_NAME = I18n.createComponentTranslatable(PURCHASE_LIMIT_KEY + "name"),
                PURCHASE_LIMIT_LIMIT = I18n.createComponentTranslatable(PURCHASE_LIMIT_KEY + "limit"),
                PURCHASE_LIMIT_REMAINING = I18n.createComponentTranslatable(PURCHASE_LIMIT_KEY + "remaining"),
                PURCHASE_LIMIT_PREVIOUS = I18n.createComponentTranslatable(PURCHASE_LIMIT_KEY + "previous-purchases"),
                PURCHASE_LIMIT_PREVIOUS_ENTRY = I18n.createComponentTranslatable(PURCHASE_LIMIT_KEY + "previous-purchase-entry");
        protected void updatePurchaseLimit(Player player, InventoryContents contents) {
            // purchase limit
            if (shop.purchaseLimitTemplate != null) {
                var records = PlayerPurchases.getCopy(player).applyTemplate(shop.purchaseLimitTemplate);
                List<PlayerPurchases.Record> entries = records.getEntries();
                List<Component> lore = new ArrayList<>(entries.size() + 3);
                // header
                lore.add(PURCHASE_LIMIT_LIMIT.apply(
                        Component.text(shop.purchaseLimit),
                        DateTimeUtils.formatReadableDuration(shop.purchaseLimitTemplate.retentionTime(), player.locale())
                ));
                lore.add(null); //ayo
                // entries
                int total = 0;
                if (!entries.isEmpty())
                    lore.add(PURCHASE_LIMIT_PREVIOUS.apply());
                LocalDateTime now = LocalDateTime.now();
                for (var entry : entries) {
                    lore.add(PURCHASE_LIMIT_PREVIOUS_ENTRY.apply(
                            Component.text(entry.amount()),
                            DateTimeUtils.formatReadableDuration(Duration.between(entry.timeOfPurchase(), now), player.locale())
                    ));
                    total += entry.amount();
                }
                lore.set(1, PURCHASE_LIMIT_REMAINING.apply(Component.text(shop.purchaseLimit - total)));
                contents.set(5, 0, ItemBuilder.of(Material.CLOCK)
                        .name(PURCHASE_LIMIT_NAME.apply())
                        .lore(lore)
                        .toEmptyClickable()
                );
            }
        }
    }
}
