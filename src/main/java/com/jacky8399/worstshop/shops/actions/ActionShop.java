package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import com.jacky8399.worstshop.shops.commodity.CommodityItem;
import com.jacky8399.worstshop.shops.commodity.CommodityMultiple;
import com.jacky8399.worstshop.shops.commodity.IFlexibleCommodity;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import com.jacky8399.worstshop.shops.rendering.SlotFiller;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ActionShop extends Action {
    public Commodity cost, reward;
    public PlayerPurchaseRecords.RecordTemplate purchaseLimitTemplate;
    public int purchaseLimit;

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
            purchaseLimitTemplate = PlayerPurchaseRecords.RecordTemplate.fromConfig(purchaseLimitYaml);
            purchaseLimit = purchaseLimitYaml.get("limit", Integer.class);
        });
    }

    private static Commodity findParent() {
        StaticShopElement element = ParseContext.findLatest(StaticShopElement.class);
        return element != null ? new CommodityItem(element.rawStack.clone()) : null;
    }

    public ActionShop(Commodity cost, Commodity reward, PlayerPurchaseRecords.RecordTemplate purchaseLimitTemplate, int purchaseLimit) {
        super(null);
        this.cost = cost;
        this.reward = reward;
        this.purchaseLimitTemplate = purchaseLimitTemplate;
        this.purchaseLimit = purchaseLimit;
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
        return map;
    }

    public void doTransaction(Player player, double count) {
        if (count == 0) {
            player.sendMessage(formatPurchaseMessage(player, 0));
            return;
        }
        ActionShop adjusted = adjustForPlayer(player);
        Commodity multipliedCost = adjusted.cost.multiply(count);
        int playerMaxPurchases = getPlayerMaxPurchase(player);
        if (count > playerMaxPurchases) {
            player.sendMessage(formatPurchaseLimitMessage(player));
            return;
        }
        if (multipliedCost.canAfford(player)) {
            multipliedCost.deduct(player);
            double refund = adjusted.reward.multiply(count).grantOrRefund(player);
            if (refund > 0) {
                // refunds DO NOT allow refunding
                adjusted.cost.multiply(refund).grantOrRefund(player);
                player.sendMessage(formatRefundMessage(player, refund));
            }
            // record the sale
            if (purchaseLimitTemplate != null) {
                PlayerPurchaseRecords records = PlayerPurchaseRecords.getCopy(player);
                records.applyTemplate(purchaseLimitTemplate).addRecord(LocalDateTime.now(), (int) (count - refund));
                records.updateFor(player);
            }
            player.sendMessage(formatPurchaseMessage(player, count - refund));
        } else {
            player.sendMessage(formatPurchaseMessage(player, 0));
        }
    }

    public void sellAll(Player player) {
        ActionShop adjusted = adjustForPlayer(player);
        int transactionCount = Math.min(adjusted.cost.getMaximumMultiplier(player), getPlayerMaxPurchase(player));
        adjusted.doTransaction(player, transactionCount);
    }

    public ActionShop adjustForPlayer(Player player) {
        return new ActionShop(
                cost instanceof IFlexibleCommodity ?
                        ((IFlexibleCommodity) cost).adjustForPlayer(player) : cost,
                reward instanceof IFlexibleCommodity ?
                        ((IFlexibleCommodity) reward).adjustForPlayer(player) : reward,
                purchaseLimitTemplate, purchaseLimit);
    }

    public int getPlayerMaxPurchase(Player player) {
        if (purchaseLimitTemplate == null) {
            return Integer.MAX_VALUE;
        }
        PlayerPurchaseRecords records = PlayerPurchaseRecords.getCopy(player);
        PlayerPurchaseRecords.RecordStorage storage = records.applyTemplate(purchaseLimitTemplate);
        int totalPurchases = storage.getTotalPurchases();
        return purchaseLimit - totalPurchases;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Optional<InventoryContents> parentContentsOptional = WorstShop.get().inventories.getContents(player);
        if (parentContentsOptional.isPresent()) {
            InventoryContents parentContents = parentContentsOptional.get();
            SmartInventory inv = ShopGui.getInventory(player, this.adjustForPlayer(player), parentContents.inventory());
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
        PlayerPurchaseRecords records = PlayerPurchaseRecords.getCopy(player);
        PlayerPurchaseRecords.RecordStorage storage = records.applyTemplate(purchaseLimitTemplate);
        List<Map.Entry<LocalDateTime, Integer>> entries = storage.getEntries();
        Duration wait;
        if (entries.size() == 0) {
            wait = Duration.ofSeconds(0);
        } else {
            LocalDateTime firstPurchase = entries.get(0).getKey();
            LocalDateTime nextPurchase = firstPurchase.plus(storage.retentionTime);
            wait = Duration.between(LocalDateTime.now(), nextPurchase);
        }
        return I18n.translate("worstshop.messages.shops.transaction-limit-reached",
                purchaseLimit,
                DateTimeUtils.formatTime(purchaseLimitTemplate.retentionTime),
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
        protected final ActionShop shop;
        protected final Commodity cost;
        protected final Commodity reward;
        protected ShopElement costElem, rewardElem;

        protected ShopGui(ActionShop shop) {
            this.shop = shop;
            this.cost = shop.cost;
            this.reward = shop.reward;
        }

        public static SmartInventory getInventory(Player player, ActionShop shop, SmartInventory parent) {
            return WorstShop.buildGui("worstshop:shop_gui")
                    .title(I18n.translate("worstshop.messages.shops.shop", player))
                    .type(InventoryType.CHEST).size(6, 9)
                    .provider(new ShopGui(shop)).parent(parent)
                    .build();
        }

        protected static final ClickableItem FILLER = ClickableItem
                .empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());
        protected static final ClickableItem ARROW = ClickableItem
                .empty(ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());
        protected static final ClickableItem GREEN = ClickableItem
                .empty(ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());
        protected static final ClickableItem RED = ClickableItem
                .empty(ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                        .amount(1).name(ChatColor.BLACK.toString()).build());

        @Override
        public void init(Player player, InventoryContents contents) {
            contents.fill(FILLER);

            updateCommodities(player, contents, false);

            updateItemCount(player, contents);
            if (cost.canMultiply() && reward.canMultiply())
                populateBuyCountChangeButtons(player, contents);
            updateCanAfford(player, contents);
            updateAnimation(player, contents);

            // player balance etc
            contents.set(5, 4, ClickableItem.empty(
                    ItemBuilder.of(Material.PLAYER_HEAD)
                            .name(ChatColor.WHITE + player.getDisplayName()).skullOwner(player)
                            .lore(Arrays.asList(cost.getPlayerTrait(player).split("\\n"))).build()
            ));

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
            // purchase limit
            if (shop.purchaseLimitTemplate != null) {
                List<String> lore = Lists.newArrayList(I18n.translate(
                        I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.limit",
                        shop.purchaseLimit, DateTimeUtils.formatTime(shop.purchaseLimitTemplate.retentionTime)
                ), I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.previous-purchases"));
                PlayerPurchaseRecords.RecordStorage records = PlayerPurchaseRecords.getCopy(player).applyTemplate(shop.purchaseLimitTemplate);

                LocalDateTime now = LocalDateTime.now();
                records.getEntries().stream()
                        .map(entry ->
                                I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.previous-purchase-entry",
                                        entry.getValue(), DateTimeUtils.formatTime(Duration.between(entry.getKey(), now)))
                        )
                        .forEach(lore::add);
                contents.set(5, 0, ClickableItem.empty(
                        ItemBuilder.of(Material.CLOCK)
                                .name(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.purchase-limit.name"))
                                .lore(lore).build()
                ));
            }
            // maximize purchase button
            if (cost.canMultiply() && reward.canMultiply())
                contents.set(5, 8, ClickableItem.of(
                        ItemBuilder.of(Material.HOPPER)
                                .name(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.buttons.maximize-purchase"))
                                .build(),
                        e -> {
                            buyCount = Math.min(
                                    Math.min(cost.getMaximumMultiplier(player), reward.getMaximumPurchase(player)),
                                    shop.getPlayerMaxPurchase(player)
                            );
                            firstClick = false;
                        }
                ));
        }

        // animation
        protected int tickCounter = 4; // to display animation on init
        protected int animationSequence = 0;
        protected int buyCount = 1;
        protected int lastBuyCount = buyCount;

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
                buyCount = newValue;
                firstClick = false;
            };
        }

        // create button pairs
        protected void populateBuyCountChangeButtons(Player player, InventoryContents contents) {
            int oldCount = firstClick ? 0 : buyCount;
            int maxPurchase = Math.min(shop.getPlayerMaxPurchase(player),
                    Math.min(cost.getMaximumMultiplier(player), reward.getMaximumPurchase(player)));
            for (int i = 0; i < BUTTON_DELTA.length; i++) {
                int index = i + 1;
                int number = BUTTON_DELTA[i];

                // increase by x
                if (oldCount + number <= maxPurchase) {
                    contents.set(4, 4 + index, ItemBuilder.of(Material.LIME_STAINED_GLASS)
                            .name(I18n.translate("worstshop.messages.shops.buy-counts.increase-by", number))
                            .lores(I18n.translate(
                                    "worstshop.messages.shops.buy-counts.change-result", oldCount + number
                            ))
                            .amount(number)
                            .toClickable(createBuyCountChanger(oldCount + number))
                    );
                } else {
                    contents.set(4, 4 + index, ItemBuilder.of(Material.GRAY_STAINED_GLASS)
                            .name(I18n.translate("worstshop.messages.shops.buy-counts.increase-by", number))
                            .lores(I18n.translate(
                                    "worstshop.messages.shops.buy-counts.change-result", maxPurchase
                            ))
                            .amount(number)
                            .toClickable(createBuyCountChanger(maxPurchase))
                    );
                }
                // decrease by x
                if (oldCount - number >= 1) {
                    contents.set(4, 4 - index, ItemBuilder.of(Material.RED_STAINED_GLASS)
                            .name(I18n.translate("worstshop.messages.shops.buy-counts.decrease-by", number))
                            .lores(I18n.translate(
                                    "worstshop.messages.shops.buy-counts.change-result", oldCount - number
                            ))
                            .amount(number)
                            .toClickable(createBuyCountChanger(oldCount - number))
                    );
                } else {
                    contents.set(4, 4 - index, ItemBuilder.of(Material.GRAY_STAINED_GLASS)
                            .name(I18n.translate("worstshop.messages.shops.buy-counts.decrease-by", number))
                            .lores(I18n.translate(
                                    "worstshop.messages.shops.buy-counts.change-result", 1
                            ))
                            .amount(number)
                            .toClickable(createBuyCountChanger(1))
                    );
                }
            }
        }

        @Override
        public void update(Player player, InventoryContents contents) {
            if (lastBuyCount != buyCount) {
                updateItemCount(player, contents);
                updateCanAfford(player, contents);
                populateBuyCountChangeButtons(player, contents);
                lastBuyCount = buyCount;
            }
            updateAnimation(player, contents);
        }

        private static final Shop FAKE_SHOP = new Shop();
        static {
            FAKE_SHOP.id = "worstshop_internal:shop_gui";
        }
        private void populateItems(Player player, ShopElement element, InventoryContents contents) {
            ShopRenderer fake = new ShopRenderer(FAKE_SHOP, player);
            SlotFiller filler = element.getFiller(fake);
            Collection<SlotPos> posList = filler.fill(element, fake);
            if (posList != null) {
                for (SlotPos pos : posList) {
                    contents.set(pos, ClickableItem.empty(element.createStack(fake, pos)));
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
                if (parent.isPresent() && parent.get().getProvider() instanceof Shop) {
                    parentShop = ((Shop) parent.get().getProvider()).id;
                }
                RuntimeException wrapped = new RuntimeException("Rendering " + stage + " element for " + player.getName() + " (@" + parentShop + ")", ex);
                // spam the player
                contents.fill(ItemUtils.getClickableErrorItem(wrapped));
            }
        }

        protected void updateItemCount(Player player, InventoryContents contents) {
            List<String> lore = reward.canMultiply() ?
                    Collections.singletonList(I18n.translate("worstshop.messages.shops.buy-counts.total-result",
                            reward.multiply(buyCount).getPlayerResult(player, Commodity.TransactionType.REWARD))) :
                    Collections.emptyList();
            boolean useChest = buyCount > 64 && (cost instanceof CommodityItem || reward instanceof CommodityItem);
            contents.set(4, 4, ItemBuilder.of(useChest ? Material.CHEST :
                            buyCount != 0 ? Material.END_CRYSTAL : Material.BARRIER)
                    .name(I18n.translate("worstshop.messages.shops.buy-counts.total", buyCount))
                    .amount(Math.max(Math.min(useChest ? (int) Math.ceil(buyCount / 64f) : buyCount, 64), 1))
                    .lore(lore)
                    .toEmptyClickable()
            );
        }

        protected void updateCanAfford(Player player, InventoryContents contents) {
            if (cost.multiply(buyCount).canAfford(player) && buyCount <= shop.getPlayerMaxPurchase(player)) {
                // green
                contents.fillRow(3, GREEN);
            } else {
                // red
                contents.fillRow(3, RED);
            }
        }

        final int calculateItemColumn() {
            return (animationSequence == 4 ? 2 : animationSequence + 3);
        }

        protected void updateAnimation(Player player, InventoryContents contents) {
            int animationCooldown = contents.property("animationCooldown", 0);
            if (animationCooldown == 0) {
                contents.setProperty("animationCooldown", 3);
            } else {
                contents.setProperty("animationCooldown", animationCooldown - 1);
                return;
            }
            // clear last arrow
            contents.set(0, animationSequence + 2, FILLER);
            contents.set(1, calculateItemColumn(), FILLER);
            contents.set(2, animationSequence + 2, FILLER);

            animationSequence = animationSequence == 4 ? 0 : animationSequence + 1;

            contents.set(0, animationSequence + 2, ARROW);
            contents.set(1, calculateItemColumn(), ARROW);
            contents.set(2, animationSequence + 2, ARROW);

            // also update item lol
            if (animationSequence == 3) {
                updateCommodities(player, contents, true);
            }
        }
    }
}
